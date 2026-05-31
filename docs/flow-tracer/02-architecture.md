# 02 — Flow Tracer: Architecture

## Module Layout

Flow Tracer lives in a dedicated Maven module `agentic-docs-flow` so it can be
included or excluded from any Spring Boot project without touching other modules.

```
agentic-docs/                       ← root pom (5 modules)
├── agentic-docs-core/              ← vector store + chat + endpoint metrics
├── agentic-docs-flow/              ← Flow Tracer
│   └── src/main/java/com/agentic/docs/flow/
│       ├── model/
│       │   ├── TraceEvent.java        ← one method-call snapshot
│       │   ├── FlowRequest.java       ← what the UI sends
│       │   └── FlowDoneEvent.java     ← final SSE event
│       ├── spi/                       ← interfaces (SOLID: DIP + ISP)
│       │   ├── TraceEventSink.java    ← pushStep / pushDone / pushError
│       │   └── TraceEmitterProvider.java ← register / attach
│       ├── serializer/
│       │   └── TraceSerializer.java   ← JSON serialisation (SRP)
│       ├── url/
│       │   └── FlowUrlBuilder.java    ← URL + path-param resolution (SRP)
│       ├── registry/
│       │   └── FlowSseRegistry.java   ← implements TraceEventSink + TraceEmitterProvider
│       ├── aspect/
│       │   └── FlowAspect.java        ← AOP interception point
│       ├── executor/
│       │   └── FlowExecutorService.java ← fires the outbound HTTP call
│       ├── controller/
│       │   └── FlowController.java    ← REST API: execute + SSE stream
│       └── autoconfigure/
│           └── AgenticDocsFlowAutoConfiguration.java
├── agentic-docs-spring-boot-starter/ ← bundles core + flow
├── agentic-docs-sample-app/         ← demo application
└── agentic-docs-ui/                 ← React 18 + Vite frontend
    └── src/
        ├── api/flowApi.js          ← HTTP + SSE client
        ├── hooks/useFlowTracer.js  ← state machine
        └── components/
            ├── FlowTracer.jsx      ← full page component
            └── FlowStepCard.jsx    ← one animated step card
```

---

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│  Browser (React)                                                │
│                                                                 │
│  FlowTracer.jsx                                                 │
│    │  user picks endpoint + fills in params/body               │
│    ↓                                                            │
│  flowApi.executeFlow(request)  ──── POST /agentic-docs/api/flow/execute ─────┐
│    │                                                            │             │
│    │  receives { traceId }                                      │             │
│    │                                                            │  Spring     │
│  flowApi.subscribeToTrace(traceId) ─── GET /agentic-docs/api/flow/trace/{id} │
│    │                                   (SSE stream)            │             │
│    │  EventSource.onmessage(step)  ←── SSE event: step         │             │
│    │  EventSource.onmessage(done)  ←── SSE event: done         │             │
│    ↓                                                            │             │
│  FlowStepCard.jsx (renders each step as it arrives)            │             │
└─────────────────────────────────────────────────────────────────┘             │
                                                                                │
┌───────────────────────────────────────────────────────────────────────────────┘
│  Spring Boot (agentic-docs-flow module)
│
│  FlowController.POST /execute
│    1. generates traceId (UUID)
│    2. TraceEmitterProvider.register(traceId)    ← reserve a slot
│    3. FlowExecutorService.executeAsync(traceId, request)  ← virtual thread
│    4. returns { traceId }
│
│  FlowController.GET /trace/{traceId}
│    1. creates SseEmitter (timeout 60s)
│    2. TraceEmitterProvider.attach(traceId)
│       → replays any buffered events (if execution already started)
│       → stores emitter for future events
│    3. returns SseEmitter (Spring keeps connection open)
│
│  FlowExecutorService (virtual thread)
│    1. FlowUrlBuilder.build(request)  ← resolves path params
│    2. fires RestClient request with X-Flow-Trace-Id: {traceId} header
│       → this hits the real endpoint inside the same JVM
│    3. on response: TraceEventSink.pushDone(traceId, ...)
│    4. on exception: TraceEventSink.pushError(traceId, ...)
│
│  FlowAspect (@Around all @Service + @RestController + @Repository)
│    1. reads X-Flow-Trace-Id from RequestContextHolder
│    2. if null → proceed normally (no-op)
│    3. assigns stepIndex (AtomicInteger per traceId)
│    4. TraceSerializer.serializeArgs(args)   ← JSON (max 2KB)
│    5. calls proceed() (the real method)
│    6. TraceSerializer.serializeValue(result)
│    7. TraceEventSink.pushStep(traceId, TraceEvent)
│       → browser receives live step card
│
└───────────────────────────────────────────────────────────────────────────────
```

---

## SSE Data Flow

```
Timeline →

Browser           FlowController         FlowSseRegistry          FlowAspect
   │                    │                       │                       │
   │── POST /execute ──→│                       │                       │
   │                    │── register(id) ──────→│                       │
   │                    │── runAsync(req,id) ───┼──────────────────────→│ (virtual thread starts)
   │←── { traceId } ───│                       │                       │
   │                    │                       │                       │
   │── GET /trace/id ──→│                       │                       │
   │                    │── attach(id,emitter) →│                       │
   │                    │                       │                       │ AOP fires:
   │                    │                       │←── pushStep(step1) ───│
   │←── event:step ────│←── send(step1) ───────│                       │
   │                    │                       │←── pushStep(step2) ───│
   │←── event:step ────│←── send(step2) ───────│                       │
   │                    │                       │  (N more steps...)    │
   │                    │                       │                       │
   │                    │                       │←── pushDone() ────────│ (executor got response)
   │←── event:done ────│←── send(done) ─────────│                      │
   │                    │                       │── complete emitter    │
   │                    │                       │── cleanup entry       │
```

---

## Technology Choices

| Concern | Choice | Reason |
|---|---|---|
| Server push | SSE (Server-Sent Events) | Simpler than WebSocket; unidirectional; native browser support via `EventSource` |
| Thread model | Virtual threads (`Thread.ofVirtual()`) | Non-blocking; can have thousands of concurrent traces without thread-pool exhaustion |
| AOP engine | Spring AOP (proxy-based) | Zero-bytecode-manipulation; works with existing `@Service`/`@Repository` beans; no agent required |
| HTTP client | Spring `RestClient` | Modern, fluent API; part of Spring 6 / Boot 3; no extra dependency |
| State buffer | `CopyOnWriteArrayList` per trace | Safe for concurrent reads from SSE thread + concurrent writes from AOP thread |
| Frontend state | Custom React hook (`useFlowTracer`) | Single source of truth; clean separation from UI components |
| Styling | Tailwind CSS v4 | Consistent with the rest of the UI; no custom CSS needed |

---

## Module Dependency Graph

```
agentic-docs-sample-app
    └── agentic-docs-spring-boot-starter
            ├── agentic-docs-core
            └── agentic-docs-flow
                    ├── spring-boot-starter-web   (provided)
                    ├── spring-boot-starter-aop
                    └── spring-boot-autoconfigure (provided)
```

`agentic-docs-flow` has **no dependency on `agentic-docs-core`** — it is fully
self-contained and can be added to any Spring Boot 3.x project.

---

## Auto-Configuration Guard

Flow Tracer is **disabled by default**:

```properties
# application.properties — must explicitly opt in
agentic.docs.flow.enabled=true
```

The `@ConditionalOnProperty` on `AgenticDocsFlowAutoConfiguration` ensures that
when the property is absent or `false`, Spring Boot skips the entire module:
no `@Aspect`, no `SseEmitter`, no `FlowController`, no beans registered.
This means there is zero overhead in production unless the property is set.
