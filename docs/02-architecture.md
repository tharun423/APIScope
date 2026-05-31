# 02 — Architecture

## Module Structure

The project is a **Maven multi-module build** with a clean separation of concerns across four modules:

```
agentic-docs-parent/                  ← Root POM (dependency management only)
├── agentic-docs-core/                ← All business logic (scan, ingest, chat, ports)
├── agentic-docs-spring-boot-starter/ ← AutoConfiguration + pre-built UI static files
├── agentic-docs-sample-app/          ← Runnable demo application
└── agentic-docs-ui/                  ← React 18 + Tailwind CSS source (build-time only)
```

### Why this split?

| Module | Responsibility | Depends on |
|---|---|---|
| `core` | Pure logic — no Spring Boot opinions | `spring-ai-core`, `spring-web` (provided) |
| `starter` | Wires core into any Spring Boot app | `core`, `spring-ai-starter-model-ollama` or `spring-ai-openai` |
| `sample-app` | Demonstrates the starter in action | `starter` |
| `ui` | React source — compiled at build time | npm only |

The `core` module has `spring-boot-starter-web` as `provided` scope. This means it compiles against Spring MVC types but does not pull in an embedded Tomcat — that comes from the host application. This is the correct pattern for library modules.

---

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                     Host Spring Boot App                        │
│                                                                 │
│  ┌──────────────────┐    ApiScanCompletedEvent                  │
│  │  @RestController │ ──────────────────────────────────┐       │
│  │  PaymentsCtrl    │                                   ▼       │
│  └──────────────────┘                    ┌──────────────────────┤
│                                          │  ApiMetadataScanner  │
│  ┌──────────────────────────────────┐    │  (reads handler      │
│  │   agentic-docs-spring-boot-      │    │   mappings, publishes│
│  │   starter                        │    │   domain event)      │
│  │                                  │    └──────────┬───────────┤
│  │  AgenticDocsAutoConfiguration    │               │           │
│  │  @ConditionalOnProperty(         │               ▼           │
│  │    agentic.docs.enabled=true)    │    ┌──────────────────────┤
│  │  @ComponentScan(core)            │    │  ApiDocumentIngestor │
│  └──────────────────────────────────┘    │  (converts to        │
│                                          │   Documents, calls   │
│  ┌──────────────────────────────────┐    │   vectorStore.add()) │
│  │   VectorStoreConfig              │    └──────────┬───────────┤
│  │   SimpleVectorStore (file-backed)│◄──────────────┘           │
│  └──────────────────┬───────────────┘                           │
│                     │  VectorStorePort.findRelevantContext()    │
│                     ▼                                           │
│  ┌──────────────────────────────────┐                           │
│  │  AgenticDocsChatService          │                           │
│  │  (implements ChatPort)           │◄── RateLimitInterceptor   │
│  │  sanitize() → RAG → LlmPort     │    (per-IP token bucket)  │
│  └──────────────────┬───────────────┘                           │
│                     │  LlmPort.complete() / LlmPort.stream()   │
│                     ▼                                           │
│  ┌──────────────────────────────────┐                           │
│  │  LlmAdapter                      │                           │
│  │  (Spring AI ChatClient)          │                           │
│  │  → OpenAI gpt-4o-mini            │                           │
│  │  → Ollama llama3.2 (local)       │                           │
│  └──────────────────────────────────┘                           │
│                                                                 │
│  ┌──────────────────────────────────┐                           │
│  │  AgenticDocsChatController       │◄── POST /agentic-docs/    │
│  │  POST /agentic-docs/api/chat     │        api/chat           │
│  │  POST /agentic-docs/api/chat/    │    POST /agentic-docs/    │
│  │       stream (SSE)               │        api/chat/stream    │
│  └──────────────────────────────────┘                           │
│                                                                 │
│  ┌──────────────────────────────────┐                           │
│  │  Static Resources                │                           │
│  │  /agentic-docs/ → React UI       │◄── Browser               │
│  │  (served by Spring Boot)         │                           │
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
               ├── Skips com.agentic.docs.core.** (internal endpoints)
               ├── Extracts: path, httpMethod, controllerName, methodName,
               │           description, pathParams, queryParams,
               │           requestBodyType, responseType
               ├── Stores List<ApiEndpointMetadata> (immutable)
               └── Publishes ApiScanCompletedEvent (domain event)
                       │
                       ▼
               ApiDocumentIngestor.onScanCompleted()
                       │
                       ├── If vector store JSON file exists on disk → skip ingest
                       │         (embeddings already loaded by VectorStoreConfig)
                       ├── Maps each endpoint → Document(toLlmReadableText(), metadata map)
                       └── Calls vectorStore.add(documents)
                               │
                               └── EmbeddingModel.embed(text) → float[] vector
                                       stored in SimpleVectorStore
                                       (saved to ./agentic-docs-vector-store.json on shutdown)
```

## Data Flow — Chat Request

```
User types question in React UI
       │
       ▼
POST /agentic-docs/api/chat
{ "question": "How do I cancel a subscription with a partial refund?" }
       │
       ▼
RateLimitInterceptor.preHandle()    ← per-IP Bucket4j token bucket
       │  (if limit exceeded → HTTP 429)
       ▼
AgenticDocsChatController.chat()
       │
       ▼
AgenticDocsChatService.answer()
       │
       ├── sanitize(question)        ← truncate to 800 chars, block injection patterns
       │
       ├── vectorStorePort.findRelevantContext(question, topK)
       │       │
       │       └── VectorStoreAdapter → vectorStore.similaritySearch(query, topK=5)
       │               → EmbeddingModel.embed(question) → cosine similarity
       │               → returns top-5 Document text chunks
       │
       ├── Joins chunks into context string (separated by ---\n)
       │
       ├── Resolves system prompt (user-configured or DEFAULT_SYSTEM_PROMPT)
       │
       └── llmPort.complete(systemPrompt, context, question)
               │
               └── LlmAdapter → chatClient.prompt()
                       .system(s → s.text(template).param("context", context))
                       .user(question)
                       .call().content()
                               │
                               └── OpenAI API / Ollama (llama3.2)
               │
               ▼
       ChatResponse { answer: "..." }
               │
               ▼
       React renders answer as Markdown
```

---

## Dependency Graph

```
agentic-docs-sample-app
    └── agentic-docs-spring-boot-starter
            ├── agentic-docs-core
            │       ├── spring-ai-core
            │       ├── spring-boot-starter-web (provided)
            │       ├── bucket4j-core (rate limiting)
            │       └── swagger-annotations (optional)
            ├── spring-ai-starter-model-ollama   ← default (local, free)
            │   OR
            │   spring-ai-openai-spring-boot-starter  ← cloud, paid
            └── spring-ai-simple-vector-store
```

---

## URL Map

| URL | What serves it |
|---|---|
| `GET /agentic-docs/` | React SPA (index.html from static resources) |
| `GET /agentic-docs/assets/*` | Vite-built JS/CSS bundles |
| `GET /agentic-docs/api/endpoints` | `AgenticDocsChatController` — lists all scanned endpoints |
| `POST /agentic-docs/api/chat` | `AgenticDocsChatController` — blocking RAG chat |
| `POST /agentic-docs/api/chat/stream` | `AgenticDocsChatController` — streaming SSE chat |
| `GET /agentic-docs/api/chat` | `AgenticDocsChatController` — returns 405 with usage hint |
| `GET /swagger-ui.html` | Springdoc (sample app only) |
| `GET /api/v1/**` | Sample controllers (`PaymentsController`, etc.) |

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
│  ┌──────────────────────────────────┐    │  (reads handler      │
│  │   agentic-docs-spring-boot-      │    │   mappings)          │
│  │   starter                        │    └──────────┬───────────┤
│  │                                  │               │           │
│  │  AgenticDocsAutoConfiguration    │               ▼           │
│  │  @ConditionalOnProperty(         │    ┌──────────────────────┤
│  │    agentic.docs.enabled=true)    │    │  ApiDocumentIngestor │
│  │  @ComponentScan(core)            │    │  (converts to        │
│  └──────────────────────────────────┘    │   Documents,         │
│                                          │   calls vectorStore) │
│  ┌──────────────────────────────────┐    └──────────┬───────────┤
│  │   VectorStoreConfig              │               │           │
│  │   SimpleVectorStore (in-memory)  │◄──────────────┘           │
│  └──────────────────┬───────────────┘                           │
│                     │  similaritySearch(query, topK=5)          │
│                     ▼                                           │
│  ┌──────────────────────────────────┐                           │
│  │  AgenticDocsChatController       │◄── POST /agentic-docs/    │
│  │  POST /agentic-docs/api/chat     │        api/chat           │
│  │  → inject context → ChatClient  │                           │
│  └──────────────────┬───────────────┘                           │
│                     │                                           │
│                     ▼                                           │
│  ┌──────────────────────────────────┐                           │
│  │  OpenAI ChatModel (gpt-4o-mini)  │                           │
│  │  OpenAI EmbeddingModel           │                           │
│  │  (text-embedding-3-small)        │                           │
│  └──────────────────────────────────┘                           │
│                                                                 │
│  ┌──────────────────────────────────┐                           │
│  │  Static Resources                │                           │
│  │  /agentic-docs/ → React UI       │◄── Browser               │
│  │  (served by Spring Boot)         │                           │
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
       ├─► ApiMetadataScanner.onApplicationEvent()
       │       │
       │       ├── Calls handlerMapping.getHandlerMethods()
       │       ├── Filters to @RestController beans only
       │       ├── Extracts: path, httpMethod, controllerName, methodName
       │       ├── Reads @Operation(summary) reflectively (optional)
       │       └── Stores List<ApiEndpointMetadata> (immutable)
       │
       └─► ApiDocumentIngestor.ingest()  [@Order(1) — runs after scanner]
               │
               ├── Reads scanner.getScannedEndpoints()
               ├── Maps each → Document(toLlmReadableText(), metadata map)
               └── Calls vectorStore.add(documents)
                       │
                       └── EmbeddingModel.embed(text) → float[] vector
                               stored in SimpleVectorStore (in-memory HashMap)
```

## Data Flow — Chat Request

```
User types question in React UI
       │
       ▼
POST /agentic-docs/api/chat
{ "question": "How do I cancel a subscription with a partial refund?" }
       │
       ▼
AgenticDocsChatController.chat()
       │
       ├── vectorStore.similaritySearch(query, topK=5)
       │       │
       │       ├── EmbeddingModel.embed(question) → query vector
       │       └── Cosine similarity against all stored vectors
       │               → returns top-5 Document objects
       │
       ├── Joins Document.getText() → context string
       │
       ├── chatClient.prompt()
       │       .system(SYSTEM_PROMPT with {context} injected)
       │       .user(question)
       │       .call().content()
       │               │
       │               └── OpenAI API call (gpt-4o-mini)
       │
       └── Returns ChatResponse { answer: "..." }
               │
               ▼
       React renders answer as Markdown
```

---

## Dependency Graph

```
agentic-docs-sample-app
    └── agentic-docs-spring-boot-starter
            ├── agentic-docs-core
            │       ├── spring-ai-core
            │       ├── spring-boot-starter-web (provided)
            │       └── swagger-annotations (optional)
            ├── spring-ai-openai-spring-boot-starter
            │       └── spring-ai-core
            │       └── spring-boot-autoconfigure
            └── spring-ai-simple-vector-store
```

---

## URL Map

| URL | What serves it |
|---|---|
| `GET /agentic-docs/` | React SPA (index.html from static resources) |
| `GET /agentic-docs/assets/*` | Vite-built JS/CSS bundles |
| `POST /agentic-docs/api/chat` | `AgenticDocsChatController` |
| `GET /swagger-ui.html` | Springdoc (sample app only) |
| `GET /api/v1/**` | `PaymentsController` (sample app only) |
