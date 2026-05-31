# 19 — Flow Tracer: Real-Time API Execution Visualizer

## Overview

`agentic-docs-flow` is a new Maven module bundled into the existing `agentic-docs-spring-boot-starter`.  
It adds a **Flow Tracer** tab to the UI where developers pick an endpoint, fill inputs, click Send, and watch a **live step-by-step diagram light up** as the request passes through each Spring bean method — powered by **Spring AOP + SSE (Server-Sent Events)**.

Every step shows the method name, exact input, output, and duration. Errors turn the step red with a stack trace. No external infrastructure is required.

---

## Architecture

```
Browser (FlowTracer.jsx)
    │
    ├─① POST /agentic-docs/api/flow/execute  { path, method, body }
    │         │
    │         ▼
    │   FlowController generates traceId="abc-123"
    │   Returns { traceId } immediately (non-blocking)
    │         │
    ├─② GET /agentic-docs/api/flow/trace/abc-123   ← SSE stream opens
    │         │
    │   FlowExecutorService (virtual thread) → RestClient → POST /api/v1/orders
    │         │
    │         ▼
    │   [ Spring AOP intercepts every @Service / @RestController method ]
    │     @Around advice fires for each method call:
    │       - Captures inputJson (method args serialized)
    │       - Calls proceed()
    │       - Captures outputJson (return value serialized)
    │       - Measures durationMs
    │       - Pushes TraceEvent via FlowSseRegistry
    │         │
    │   Each event → FlowSseRegistry.push("abc-123", event)
    │         │
    │         ▼ (SSE stream — real-time, non-blocking)
    │   Browser receives events → FlowStepCard lights up one-by-one
    │         │
    │   Final DONE event → FlowDoneEvent with HTTP status + response body
    │         │
    └─③ SSE closes → "Final Response" card renders
```

---

## Module Layout

```
agentic-docs-flow/
├── pom.xml
└── src/main/java/com/agentic/docs/flow/
    ├── model/
    │   ├── TraceEvent.java                          ← one method call snapshot
    │   ├── FlowRequest.java                         ← input from UI
    │   └── FlowDoneEvent.java                       ← final result pushed via SSE
    ├── registry/
    │   └── FlowSseRegistry.java                     ← ConcurrentHashMap<traceId, TraceEntry>
    ├── aspect/
    │   └── FlowAspect.java                          ← @Aspect intercepts Service + Controller
    ├── executor/
    │   └── FlowExecutorService.java                 ← fires RestClient call on virtual thread
    ├── controller/
    │   └── FlowController.java                      ← POST /execute + GET /trace/{id} (SSE)
    └── autoconfigure/
        └── AgenticDocsFlowAutoConfiguration.java

src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  ← updated
```

---

## How the Modules Relate

```
root pom.xml (parent)
├── agentic-docs-core                    ← RAG + chat (UNTOUCHED)
├── agentic-docs-flow                    ← NEW: AOP tracer (zero dependency on core)
├── agentic-docs-spring-boot-starter     ← adds agentic-docs-flow as dependency
└── agentic-docs-sample-app              ← gets flow transitively, no change needed
```

Users add **one dependency** and get both RAG chat + Flow Tracer:

```xml
<dependency>
    <groupId>com.agentic.docs</groupId>
    <artifactId>agentic-docs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Flow Tracer is **off by default** in production. Enable with:

```properties
agentic.docs.flow.enabled=true
```

---

## Backend Implementation Plan (14 Steps)

### Step 1 — `agentic-docs-flow/pom.xml`

New Maven module. Parent = `agentic-docs-parent`.

Key dependencies:
- `spring-boot-starter-web` — scope `provided`
- `spring-boot-starter-aop` — brings `aspectjweaver`, enables `@Aspect`
- `spring-boot-autoconfigure` — scope `provided`
- `spring-boot-autoconfigure-processor` — optional (startup perf)
- No dependency on `agentic-docs-core` — fully independent

---

### Step 2 — `TraceEvent` record

```java
package com.agentic.docs.flow.model;

public record TraceEvent(
    String  traceId,
    int     stepIndex,
    String  layer,          // CONTROLLER | SERVICE | REPOSITORY | COMPONENT
    String  className,
    String  methodName,
    String  inputJson,      // serialized method args (capped at 2 KB)
    String  outputJson,     // serialized return value (capped at 2 KB)
    long    durationMs,
    String  status,         // EXIT | ERROR
    String  errorMessage    // null unless status=ERROR
) {}
```

---

### Step 3 — `FlowRequest` record

```java
package com.agentic.docs.flow.model;

import java.util.Map;

public record FlowRequest(
    String              httpMethod,
    String              path,
    Map<String, String> pathParams,
    String              body
) {}
```

---

### Step 4 — `FlowDoneEvent` record

```java
package com.agentic.docs.flow.model;

public record FlowDoneEvent(
    String  traceId,
    int     httpStatus,
    String  responseBody,
    long    totalMs,
    boolean ok,
    int     stepCount
) {}
```

---

### Step 5 — `FlowSseRegistry`

`@Component`. Holds `ConcurrentHashMap<String, TraceEntry>`.

`TraceEntry` contains:
- `CopyOnWriteArrayList<Object>` — buffered events (handles late SSE connections)
- `volatile SseEmitter emitter` — null until SSE endpoint is called
- `AtomicInteger stepCounter`

Methods:
- `register(traceId)` — called by controller before async execution starts
- `attach(traceId)` → `SseEmitter` — replays buffered events, then sets live emitter (60s timeout)
- `pushStep(traceId, TraceEvent)` — buffers + sends named event `"step"`
- `pushDone(traceId, FlowDoneEvent)` — sends named event `"done"`, calls `emitter.complete()`
- `pushError(traceId, message)` — sends named event `"error"`, calls `emitter.complete()`

---

### Step 6 — `FlowAspect`

```java
@Aspect
@Component
@ConditionalOnProperty("agentic.docs.flow.enabled")
public class FlowAspect {

    // Intercepts all public methods in @Service, @RestController, @Repository
    // beans of the HOST application.
    // Skips if:
    //   1. No X-Flow-Trace-Id header on the current request thread
    //   2. Class is inside com.agentic.docs (our own internals)

    @Around("""
        (within(@org.springframework.stereotype.Service *)
         || within(@org.springframework.web.bind.annotation.RestController *)
         || within(@org.springframework.stereotype.Repository *))
        && !within(com.agentic.docs..*)
        """)
    public Object trace(ProceedingJoinPoint pjp) throws Throwable { ... }
}
```

`@Around` logic:
1. Read `traceId` from `RequestContextHolder` header `X-Flow-Trace-Id`
2. If null → `return pjp.proceed()` immediately (normal requests untouched)
3. Determine `layer` from annotation present on declaring class
4. Serialize args → `inputJson` (Jackson, cap 2 KB, fallback `toString()`)
5. Record start time, call `proceed()`
6. Serialize return value → `outputJson`
7. Push `TraceEvent(status=EXIT)` via `registry.pushStep(...)`
8. On exception → push `TraceEvent(status=ERROR, errorMessage)` then rethrow

---

### Step 7 — `FlowExecutorService`

`@Service`. Injects `FlowSseRegistry` + `@Value("${server.port:8080}")`.

`executeAsync(traceId, FlowRequest)` — starts a **virtual thread**:
1. Build URL: substitute `{param}` tokens from `pathParams`
2. `RestClient.create("http://localhost:" + port)`
3. Call host endpoint with header `X-Flow-Trace-Id: traceId`
4. For POST/PUT/PATCH: send `body` with `Content-Type: application/json`
5. Use `.onStatus(HttpStatusCode::isError, ...)` to suppress exception on 4xx/5xx
6. Record `totalMs`, call `registry.pushDone(traceId, FlowDoneEvent(...))`
7. On network exception: `registry.pushError(traceId, ex.getMessage())`

---

### Step 8 — `FlowController`

```
POST /agentic-docs/api/flow/execute
  Body: FlowRequest
  Response: { "traceId": "uuid" }   ← returned immediately, execution is async

GET /agentic-docs/api/flow/trace/{traceId}
  Response: text/event-stream (SseEmitter)
  Events:
    event: step  data: { TraceEvent JSON }
    event: done  data: { FlowDoneEvent JSON }
    event: error data: { "message": "..." }
```

---

### Step 9 — `AgenticDocsFlowAutoConfiguration`

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnProperty(
    prefix = "agentic.docs.flow",
    name   = "enabled",
    havingValue = "true",
    matchIfMissing = false   // OFF by default in production
)
@ComponentScan("com.agentic.docs.flow")
public class AgenticDocsFlowAutoConfiguration {}
```

Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.agentic.docs.autoconfigure.AgenticDocsAutoConfiguration
com.agentic.docs.flow.autoconfigure.AgenticDocsFlowAutoConfiguration
```

---

### Step 10 — Root `pom.xml`

Add after `<module>agentic-docs-spring-boot-starter</module>`:

```xml
<module>agentic-docs-flow</module>
```

---

### Step 11 — `agentic-docs-spring-boot-starter/pom.xml`

Add after `agentic-docs-core` dependency:

```xml
<!-- Flow Tracer: AOP + SSE real-time execution visualizer -->
<dependency>
    <groupId>com.agentic.docs</groupId>
    <artifactId>agentic-docs-flow</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Frontend Implementation Plan

### Step 12 — `src/api/flowApi.js`

```javascript
// POST /agentic-docs/api/flow/execute → { traceId }
export async function executeFlow(request) { ... }

// Opens EventSource, maps 'step' / 'done' / 'error' events
// Returns cleanup function (closes EventSource)
export function subscribeToTrace(traceId, onStep, onDone, onError) { ... }
```

---

### Step 13 — `src/hooks/useFlowTracer.js`

State:
```javascript
{
  steps:         [],          // TraceEvent[]  — grows live as SSE events arrive
  status:        'idle',      // idle | running | done | error
  finalResponse: null,        // FlowDoneEvent
  traceId:       null
}
```

`send(request)`:
1. Reset state, set `status=running`
2. Call `executeFlow(request)` → get `traceId`
3. Call `subscribeToTrace(traceId, onStep, onDone, onError)`
4. `onStep` → append to `steps[]`
5. `onDone` → set `finalResponse`, `status=done`
6. `onError` → set `status=error`
7. Cleanup `EventSource` on unmount

---

### Step 14 — `src/components/FlowStepCard.jsx`

Animated fade-in card per `TraceEvent`:

| Section | Content |
|---|---|
| Header | Colored layer badge + `ClassName.method()` + duration chip |
| Input panel | Dark code block, copy button, `inputJson` |
| Output panel | Dark code block, copy button, `outputJson` |
| Error state | Red border, red header, collapsible stack trace panel |
| Connector | Dashed vertical line below each card (hidden on last) |

Layer badge colors:
- `CONTROLLER` → violet
- `SERVICE` → blue
- `REPOSITORY` → amber
- `COMPONENT` → slate

---

### Step 15 — `src/components/FlowTracer.jsx`

**Top section:**
- Endpoint dropdown (uses existing `useEndpoints` hook, grouped by controller)
- Dynamic path-param input fields (generated from selected endpoint's `pathParams`)
- Body textarea (shown for POST/PUT/PATCH)
- `▶ Send` button (disabled while `status=running`)

**Middle section:**
- Vertical list of `<FlowStepCard>` that fade in as SSE events arrive
- Live pulse indicator shown at bottom while `status=running`

**Bottom section:**
- `Final Response` card: HTTP status badge (green 2xx / red 4xx/5xx) + response body + total duration

---

### Step 16 — Wire Navigation

**`Header.jsx`** — add to `navItems`:
```javascript
{ id: 'flow', icon: Workflow, label: 'Flow Tracer', desc: 'Live execution trace' }
```

**`App.jsx`** — add import + tab branch:
```javascript
import FlowTracer from './components/FlowTracer'
// ...
tab === 'flow' ? <FlowTracer /> : ...
```

---

## Files Changed vs Created

| File | Action |
|---|---|
| `agentic-docs-flow/pom.xml` | CREATE |
| `agentic-docs-flow/src/.../model/TraceEvent.java` | CREATE |
| `agentic-docs-flow/src/.../model/FlowRequest.java` | CREATE |
| `agentic-docs-flow/src/.../model/FlowDoneEvent.java` | CREATE |
| `agentic-docs-flow/src/.../registry/FlowSseRegistry.java` | CREATE |
| `agentic-docs-flow/src/.../aspect/FlowAspect.java` | CREATE |
| `agentic-docs-flow/src/.../executor/FlowExecutorService.java` | CREATE |
| `agentic-docs-flow/src/.../controller/FlowController.java` | CREATE |
| `agentic-docs-flow/src/.../autoconfigure/AgenticDocsFlowAutoConfiguration.java` | CREATE |
| `agentic-docs-flow/src/main/resources/META-INF/spring/...AutoConfiguration.imports` | CREATE |
| `agentic-docs-ui/src/api/flowApi.js` | CREATE |
| `agentic-docs-ui/src/hooks/useFlowTracer.js` | CREATE |
| `agentic-docs-ui/src/components/FlowStepCard.jsx` | CREATE |
| `agentic-docs-ui/src/components/FlowTracer.jsx` | CREATE |
| `pom.xml` (root) | EDIT — add `<module>agentic-docs-flow</module>` |
| `agentic-docs-spring-boot-starter/pom.xml` | EDIT — add `agentic-docs-flow` dependency |
| `agentic-docs-spring-boot-starter/src/main/resources/META-INF/spring/...imports` | EDIT — add flow auto-config |
| `agentic-docs-ui/src/components/Header.jsx` | EDIT — add Flow Tracer nav item |
| `agentic-docs-ui/src/App.jsx` | EDIT — add flow tab branch |

---

## Key Design Decisions

| Decision | Choice | Reason |
|---|---|---|
| AOP trigger mechanism | `X-Flow-Trace-Id` request header | Avoids `ThreadLocal` leaks; clean per-request scoping |
| Execution model | Virtual thread (`Thread.ofVirtual()`) | Non-blocking; no thread pool config needed |
| SSE buffer strategy | Replay buffered events on late connect | Frontend SSE connection races with backend execution |
| Production safety | `matchIfMissing=false` on `@ConditionalOnProperty` | AOP is off unless explicitly opted in |
| Scope of AOP pointcut | Exclude `com.agentic.docs..*` | Prevents tracing our own internals |
| Input/output size cap | 2 KB per field, fallback to `toString()` | Prevents huge response payloads for binary/stream args |
| Deployment | Bundled in `agentic-docs-spring-boot-starter` | Single dependency for users; no extra artifact to add |
