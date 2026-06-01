# 03 — Backend Deep Dive

## Package Structure

```
com.apiscope.core
├── model/
│   ├── ChatRequest.java              ← record: { @NotBlank String question }
│   └── ChatResponse.java             ← record: { String answer }
├── port/
│   ├── LlmPort.java                  ← domain interface for LLM calls
│   └── VectorStorePort.java          ← domain interface for vector search
├── infrastructure/
│   ├── LlmAdapter.java               ← implements LlmPort via Spring AI ChatClient
│   └── VectorStoreAdapter.java       ← implements VectorStorePort via Spring AI VectorStore
├── scanner/
│   ├── ApiEndpointMetadata.java      ← immutable DTO record (10 fields)
│   ├── ApiMetadataScanner.java       ← endpoint discovery + publishes ApiScanCompletedEvent
│   ├── ApiScanCompletedEvent.java    ← domain event carrying discovered endpoints
│   ├── EndpointRepository.java       ← interface implemented by ApiMetadataScanner
│   └── ParameterExtractor.java       ← extracts path/query params, body type, response type
├── config/
│   ├── AgenticDocsProperties.java    ← @ConfigurationProperties record (apiscope.*)
│   ├── AgenticDocsMvcConfigurer.java ← CORS + rate-limit interceptor + UI view forwarding
│   └── VectorStoreConfig.java        ← file-backed SimpleVectorStore bean
├── ingestor/
│   └── ApiDocumentIngestor.java      ← embeds endpoints into vector store on ApiScanCompletedEvent
├── chat/
│   ├── ChatPort.java                 ← interface: answer() + streamAnswer()
│   ├── AgenticDocsChatService.java   ← implements ChatPort; orchestrates RAG pipeline
│   ├── QuestionSanitizer.java        ← truncates input + blocks prompt injection
│   ├── PromptBuilder.java            ← builds/resolves the system prompt
│   ├── ChatController.java           ← REST: POST /apiscope/api/chat + /chat/stream
│   └── EndpointController.java       ← REST: GET /apiscope/api/endpoints + POST /admin/reindex
├── metrics/
│   ├── EndpointMetricsController.java ← proxies Micrometer metrics to the UI
│   └── MetricsCalculator.java         ← calculates avg response time + success rate
└── ratelimit/
    ├── RateLimiterService.java        ← per-IP Bucket4j token bucket
    └── RateLimitInterceptor.java      ← HandlerInterceptor enforcing rate limits

com.apiscope.autoconfigure
└── AgenticDocsAutoConfiguration.java  ← Spring Boot AutoConfig (enabled by default)
```

---

## `ApiEndpointMetadata` — The Data Contract

```java
public record ApiEndpointMetadata(
        String path,                       // e.g. "/api/v1/subscriptions/{id}"
        String httpMethod,                 // e.g. "POST"
        String controllerName,             // e.g. "PaymentsController"
        String methodName,                 // e.g. "terminateSubscription"
        String description,                // from @Operation(summary) or camelCase fallback
        List<String> pathParams,           // @PathVariable names
        List<String> requiredQueryParams,  // required @RequestParam names
        List<String> optionalQueryParams,  // optional @RequestParam names
        String requestBodyType,            // @RequestBody simple class name
        String responseType                // return type, unwrapped from ResponseEntity<T>
) {}
```

`toLlmReadableText()` produces the structured plain-text that gets embedded into the vector store. A labeled format (`Endpoint: [GET] /api/v1/subscriptions/{id}`) gives the embedding model clear semantic signals compared to raw JSON.

---

## `ApiMetadataScanner` — Endpoint Discovery

Listens for `ContextRefreshedEvent` and calls `RequestMappingHandlerMapping.getHandlerMethods()` — the same authoritative source Spring Boot Actuator's `/actuator/mappings` uses. Filters out internal `com.apiscope.*` packages, then delegates all parameter extraction to `ParameterExtractor`.

```java
private ApiEndpointMetadata toMetadata(RequestMappingInfo info, HandlerMethod hm) {
    return new ApiEndpointMetadata(
            extractPath(info),
            extractHttpMethod(info),
            hm.getBeanType().getSimpleName(),
            hm.getMethod().getName(),
            extractDescription(hm.getMethod()),
            extractor.pathParams(hm),
            extractor.requiredQueryParams(hm),
            extractor.optionalQueryParams(hm),
            extractor.requestBodyType(hm),
            extractor.responseType(hm)
    );
}
```

An `AtomicBoolean` guard prevents double-scanning when Spring fires `ContextRefreshedEvent` multiple times (parent/child contexts). After scanning, it publishes `ApiScanCompletedEvent` — a domain event that decouples the scanner from the ingestor.

---

## `ParameterExtractor` — Parameter Extraction

Extracted from `ApiMetadataScanner` to keep each class focused on one job. Uses Spring's `MethodParameter` API to read annotations and compiled parameter names.

| Method | What it reads |
|---|---|
| `pathParams(hm)` | `@PathVariable` names |
| `requiredQueryParams(hm)` | `@RequestParam` where `required=true` and no `defaultValue` |
| `optionalQueryParams(hm)` | `@RequestParam` where `required=false` or has a `defaultValue` |
| `requestBodyType(hm)` | Simple class name of the `@RequestBody` parameter |
| `responseType(hm)` | Return type, unwrapping `ResponseEntity<T>` to `T` |

---

## `ApiDocumentIngestor` — Embedding and Storage

Listens for `ApiScanCompletedEvent`. On startup, skips ingest if the vector store JSON file already exists on disk (embeddings were pre-loaded by `VectorStoreConfig`). On `reindex()`, deletes the file and re-embeds everything.

```java
// Startup path — skip if already on disk
@EventListener
public void onScanCompleted(ApiScanCompletedEvent event) {
    if (!ingested.compareAndSet(false, true)) return;
    if (new File(properties.vectorStorePath()).exists()) return; // already loaded
    ingest(event.endpoints());
}

// Manual reindex path — always re-embeds
public void reindex(List<ApiEndpointMetadata> endpoints) {
    new File(properties.vectorStorePath()).delete();
    ingested.set(true);
    ingest(endpoints);
}
```

The two paths are now clearly separate — no shared private method with a `forced` boolean flag.

---

## `AgenticDocsChatService` — RAG Pipeline Orchestrator

Depends only on `VectorStorePort` and `LlmPort` — zero Spring AI imports. Delegates sanitization to `QuestionSanitizer` and prompt resolution to `PromptBuilder`.

```java
public ChatResponse answer(ChatRequest request) {
    LlmPort llm = llmProvider.getIfAvailable();
    if (llm == null) return new ChatResponse(NO_LLM_MESSAGE);

    String question = QuestionSanitizer.sanitize(request.question());
    String answer = llm.complete(promptBuilder.systemPrompt(), retrieveContext(question), question);
    return new ChatResponse(answer != null && !answer.isBlank() ? answer : FALLBACK_ANSWER);
}
```

---

## `QuestionSanitizer` — Prompt Injection Defense

A static utility class with a single `sanitize(String)` method. Two layers of defense:

1. **Truncation** — caps input at 800 characters
2. **Regex blocking** — matches common injection phrases (`ignore previous instructions`, `jailbreak`, `DAN`, etc.) and replaces the entire input with `[BLOCKED]`

Extracted from `AgenticDocsChatService` so it can be tested independently without any mocks.

---

## `PromptBuilder` — System Prompt Resolution

Reads `apiscope.system-prompt` from properties. Returns the custom prompt if set, otherwise returns `DEFAULT_SYSTEM_PROMPT`. The default prompt includes:

- A strict persona ("expert API assistant for THIS application")
- Anti-injection guardrails in the prompt text itself
- A `{context}` placeholder where retrieved endpoint chunks are injected
- A fallback instruction for when no relevant endpoint is found

Extracted from `AgenticDocsChatService` so prompt logic is testable without wiring the full RAG pipeline.

---

## `ChatController` — Chat Endpoints Only

Handles only the two chat endpoints. Rate limiting is applied upstream by `RateLimitInterceptor` — the controller has zero awareness of throttling.

| Method | Path | Description |
|---|---|---|
| `POST` | `/apiscope/api/chat` | Blocking RAG — returns full answer as JSON |
| `POST` | `/apiscope/api/chat/stream` | Streaming RAG — delivers tokens via SSE |

The streaming endpoint returns `Flux<ServerSentEvent<String>>` directly. Spring MVC 6+ handles the SSE lifecycle automatically — no manual `SseEmitter` management needed. Client disconnects cancel the upstream `Flux`, stopping Ollama token generation immediately.

---

## `EndpointController` — Endpoint Listing and Admin

Handles endpoint listing and the manual reindex trigger. No rate limiting applied here.

| Method | Path | Description |
|---|---|---|
| `GET` | `/apiscope/api/endpoints` | Returns all scanned `ApiEndpointMetadata` as JSON |
| `POST` | `/apiscope/api/admin/reindex` | Forces a full re-embed of all endpoints |

---

## `MetricsCalculator` — Metrics Logic

Extracted from `EndpointMetricsController` to keep the controller as a thin HTTP handler. Reads `http.server.requests` from Micrometer and computes:

- `avgResponseMs` — total duration / total count × 1000
- `successRate` — 2xx count / total count × 100
- `totalRequests` — raw count

Returns `{ "available": false }` when no data exists for the given URI + method.

---

## `AgenticDocsMvcConfigurer` — CORS, Rate Limiting, UI Forwarding

Three cross-cutting concerns in one place:

1. **Rate limiting** — `RateLimitInterceptor` applied to `/apiscope/api/chat` and `/apiscope/api/chat/**` only (not endpoints or metrics)
2. **CORS** — allows configured origins (`apiscope.cors.allowed-origins`) for all `/apiscope/api/**` paths
3. **UI forwarding** — forwards `/`, `/apiscope`, and `/apiscope/` to the bundled `index.html`

---

## `LlmPort` and `VectorStorePort` — Domain Ports

```java
public interface LlmPort {
    String complete(String systemPromptTemplate, String context, String question);
    Flux<String> stream(String systemPromptTemplate, String context, String question);
}

public interface VectorStorePort {
    List<String> findRelevantContext(String question, int topK);
}
```

`AgenticDocsChatService` depends only on these interfaces — no Spring AI types. `LlmAdapter` and `VectorStoreAdapter` are the only classes that import Spring AI. Swapping LLM providers requires writing a new adapter, not touching the service.

---

## `AgenticDocsAutoConfiguration` — The Starter Wiring

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnProperty(prefix = "apiscope", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgenticDocsProperties.class)
@ComponentScan(basePackages = "com.apiscope.core", excludeFilters = ...)
@Import(AgenticDocsAutoConfiguration.VectorStoreRegistrar.class)
public class AgenticDocsAutoConfiguration {}
```

- `matchIfMissing = true` — enabled by default; add `apiscope.enabled=false` to disable in production
- `@ComponentScan` discovers all `@Component`, `@RestController`, `@Configuration` beans in core automatically
- `VectorStoreRegistrar` is a nested `@Configuration` guarded by `@ConditionalOnClass(SimpleVectorStore.class)` — prevents a `NoClassDefFoundError` on apps without `spring-ai-vector-store`
