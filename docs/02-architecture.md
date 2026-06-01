# 02 — Architecture

## Module Structure

```
apiscope-parent/                       ← Root POM (dependency management only)
├── apiscope-core/                     ← All business logic (scan, ingest, chat, ports)
├── apiscope-flow/                     ← Real-time execution flow tracer (AOP + SSE)
├── apiscope-spring-boot-starter/      ← AutoConfiguration + pre-built UI static files
├── apiscope-sample-app/               ← Runnable demo application
└── apiscope-ui/                       ← React 18 + Tailwind CSS source (build-time only)
```

### Why this split?

| Module | Responsibility | Depends on |
|---|---|---|
| `apiscope-core` | Pure logic — scan, ingest, chat, ports | `spring-ai-*`, `spring-web` (provided) |
| `apiscope-flow` | AOP tracing + SSE streaming | `spring-boot-starter-aop`, `spring-web` (provided) |
| `apiscope-spring-boot-starter` | Wires core + flow into any Spring Boot app | `apiscope-core`, `apiscope-flow` |
| `apiscope-sample-app` | Demonstrates the starter in action | `apiscope-spring-boot-starter` |
| `apiscope-ui` | React source — compiled at build time | npm only |

`apiscope-core` has `spring-boot-starter-web` as `provided` scope — it compiles against Spring MVC types but does not pull in an embedded Tomcat. That comes from the host application.

---

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Host Spring Boot App                        │
│                                                                 │
│  ┌──────────────────┐    ContextRefreshedEvent                  │
│  │  @RestController │ ──────────────────────────────────┐       │
│  │  PaymentsCtrl    │                                   ▼       │
│  └──────────────────┘                    ┌──────────────────────┤
│                                          │  ApiMetadataScanner  │
│  ┌──────────────────────────────────┐    │  + ParameterExtractor│
│  │   AgenticDocsAutoConfiguration   │    │  → ApiScanCompleted  │
│  │   @ConditionalOnProperty(        │    │    Event             │
│  │     apiscope.enabled=true)       │    └──────────┬───────────┤
│  │   @ComponentScan(apiscope.core)  │               │           │
│  └──────────────────────────────────┘               ▼           │
│                                          ┌──────────────────────┤
│  ┌──────────────────────────────────┐    │  ApiDocumentIngestor │
│  │   VectorStoreConfig              │◄───│  → vectorStore.add() │
│  │   SimpleVectorStore (file-backed)│    └──────────────────────┤
│  └──────────────────┬───────────────┘                           │
│                     │  VectorStorePort.findRelevantContext()     │
│                     ▼                                           │
│  ┌──────────────────────────────────┐                           │
│  │  AgenticDocsChatService          │◄── RateLimitInterceptor   │
│  │  QuestionSanitizer               │    (per-IP token bucket,  │
│  │  PromptBuilder                   │     chat paths only)      │
│  └──────────────────┬───────────────┘                           │
│                     │  LlmPort.complete() / stream()            │
│                     ▼                                           │
│  ┌──────────────────────────────────┐                           │
│  │  LlmAdapter (Spring AI)          │                           │
│  │  → Ollama llama3.2 (local)       │                           │
│  └──────────────────────────────────┘                           │
│                                                                 │
│  ┌──────────────────────────────────┐                           │
│  │  ChatController                  │◄── POST /apiscope/api/    │
│  │  POST /apiscope/api/chat         │        chat               │
│  │  POST /apiscope/api/chat/stream  │    POST /apiscope/api/    │
│  └──────────────────────────────────┘        chat/stream        │
│                                                                 │
│  ┌──────────────────────────────────┐                           │
│  │  EndpointController              │◄── GET /apiscope/api/     │
│  │  GET  /apiscope/api/endpoints    │        endpoints          │
│  │  POST /apiscope/api/admin/reindex│                           │
│  └──────────────────────────────────┘                           │
│                                                                 │
│  ┌──────────────────────────────────┐                           │
│  │  FlowController (apiscope-flow)  │◄── POST /apiscope/api/    │
│  │  POST /apiscope/api/flow/execute │        flow/execute       │
│  │  GET  /apiscope/api/flow/trace/  │                           │
│  └──────────────────────────────────┘                           │
│                                                                 │
│  ┌──────────────────────────────────┐                           │
│  │  Static Resources                │◄── Browser               │
│  │  /apiscope/ → React UI           │                           │
│  └──────────────────────────────────┘                           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Flow — Startup (Ingestion)

```
Application starts
       │
       ▼
Spring fires ContextRefreshedEvent
       │
       └─► ApiMetadataScanner.onApplicationEvent()
               │
               ├── Calls handlerMapping.getHandlerMethods()
               ├── Filters to @RestController beans only
               ├── Skips com.apiscope.** (internal endpoints)
               ├── Delegates to ParameterExtractor for:
               │       pathParams, requiredQueryParams, optionalQueryParams,
               │       requestBodyType, responseType
               ├── Stores List<ApiEndpointMetadata> (immutable)
               └── Publishes ApiScanCompletedEvent
                       │
                       ▼
               ApiDocumentIngestor.onScanCompleted()
                       │
                       ├── If vector store JSON file exists → skip (already loaded)
                       ├── Maps each endpoint → Document(toLlmReadableText(), metadata)
                       └── vectorStore.add(documents)
                               │
                               └── EmbeddingModel.embed(text) → float[] vector
                                       saved to ./apiscope-vector-store.json on shutdown
```

---

## Data Flow — Chat Request

```
User types question in React UI
       │
       ▼
POST /apiscope/api/chat  { "question": "..." }
       │
       ▼
RateLimitInterceptor.preHandle()     ← per-IP Bucket4j token bucket
       │  (if limit exceeded → HTTP 429)
       ▼
ChatController.chat()
       │
       ▼
AgenticDocsChatService.answer()
       │
       ├── QuestionSanitizer.sanitize()   ← truncate to 800 chars, block injection
       │
       ├── retrieveContext(question)
       │       └── VectorStorePort.findRelevantContext(question, topK=5)
       │               → cosine similarity → top-5 endpoint text chunks
       │
       ├── PromptBuilder.systemPrompt()   ← custom or DEFAULT_SYSTEM_PROMPT
       │
       └── LlmPort.complete(systemPrompt, context, question)
               └── LlmAdapter → Spring AI ChatClient → Ollama / OpenAI
       │
       ▼
ChatResponse { answer: "..." }  →  React renders as Markdown
```

---

## Data Flow — Flow Tracer Request

```
User clicks "Try It" in React UI
       │
       ▼
POST /apiscope/api/flow/execute  { httpMethod, path, pathParams, body }
       │
       ▼
FlowController.execute()
       ├── Generates traceId (UUID)
       ├── emitterProvider.register(traceId)   ← reserves SSE slot
       └── executor.executeAsync(traceId, request)   ← virtual thread, returns immediately
       │
       ▼
Returns { "traceId": "uuid" } immediately
       │
       ▼ (browser opens SSE stream)
GET /apiscope/api/flow/trace/{traceId}
       │
       ▼ (virtual thread fires the actual HTTP call with X-Flow-Trace-Id header)
FlowExecutorService.execute()
       ├── buildSpec()   ← builds RestClient request with trace header
       ├── Fires HTTP call to localhost:{port}{path}
       │
       │   (FlowAspect intercepts every @Service/@RestController/@Repository method)
       │   └── pushes TraceEvent via FlowSseRegistry for each method call
       │
       └── buildDoneEvent()  ← wraps HTTP response + step count
               └── pushDone() → SSE "done" event → browser renders call chain
```

---

## URL Map

| URL | What serves it |
|---|---|
| `GET /apiscope/` | React SPA (index.html from static resources) |
| `GET /apiscope/assets/*` | Vite-built JS/CSS bundles |
| `GET /apiscope/api/endpoints` | `EndpointController` — lists all scanned endpoints |
| `POST /apiscope/api/chat` | `ChatController` — blocking RAG chat |
| `POST /apiscope/api/chat/stream` | `ChatController` — streaming SSE chat |
| `POST /apiscope/api/admin/reindex` | `EndpointController` — forces re-embed |
| `GET /apiscope/api/endpoint-metrics` | `EndpointMetricsController` — Micrometer proxy |
| `POST /apiscope/api/flow/execute` | `FlowController` — starts a traced execution |
| `GET /apiscope/api/flow/trace/{id}` | `FlowController` — SSE stream of trace events |
| `GET /swagger-ui.html` | Springdoc (sample app only) |
| `GET /api/v1/**` | Sample controllers (`PaymentsController`, etc.) |

---

## Dependency Graph

```
apiscope-sample-app
    └── apiscope-spring-boot-starter
            ├── apiscope-core
            │       ├── spring-ai-model
            │       ├── spring-ai-vector-store
            │       ├── spring-ai-client-chat
            │       ├── spring-boot-starter-web (provided)
            │       └── bucket4j_jdk17-core (rate limiting)
            ├── apiscope-flow
            │       ├── spring-boot-starter-aop
            │       └── hibernate-core (optional — SQL capture)
            └── spring-ai-starter-model-ollama  ← default (local, free)
```
