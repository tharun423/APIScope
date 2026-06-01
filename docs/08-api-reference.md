# 08 — API Reference

## Chat Endpoints

Handled by `ChatController`. Rate limiting (20 req/min per IP by default) is applied by `RateLimitInterceptor` on all `/apiscope/api/chat/**` paths.

---

### `POST /apiscope/api/chat`

Accepts a natural language question, performs RAG retrieval against the indexed endpoints, and returns an LLM-generated answer.

**Request**
```
Content-Type: application/json
```
```json
{ "question": "How do I cancel a subscription with a partial refund?" }
```

**Response — 200 OK**
```json
{ "answer": "To cancel a subscription with a partial refund, call POST /api/v1/subscriptions/{id}/terminate ..." }
```

**Error responses**

| Status | Cause |
|---|---|
| `400 Bad Request` | Missing or blank `question` field |
| `429 Too Many Requests` | Rate limit exceeded |
| `405 Method Not Allowed` | GET request sent to this path |

---

### `POST /apiscope/api/chat/stream`

Same RAG pipeline, but delivers the LLM response token-by-token via **Server-Sent Events**.

**Response — `text/event-stream`**

| Event name | Data | Meaning |
|---|---|---|
| `token` | raw token text | One piece of the LLM's answer |
| `done` | `[DONE]` | Stream completed normally |
| `error` | error message | Something went wrong |

```
event: token
data: Use

event: token
data:  POST /api/v1/subscriptions/{id}/terminate

event: done
data: [DONE]
```

Client disconnects automatically cancel the upstream `Flux`, stopping Ollama token generation immediately.

---

## Endpoint Listing & Admin

Handled by `EndpointController`. No rate limiting applied.

---

### `GET /apiscope/api/endpoints`

Returns the full list of scanned endpoints as JSON. Used by the API Explorer panel in the React UI.

```bash
curl http://localhost:8080/apiscope/api/endpoints
```

**Response — 200 OK** — array of `ApiEndpointMetadata`:

```json
[
  {
    "path": "/api/v1/subscriptions/{id}",
    "httpMethod": "GET",
    "controllerName": "PaymentsController",
    "methodName": "getSubscription",
    "description": "Get Subscription",
    "pathParams": ["id"],
    "requiredQueryParams": [],
    "optionalQueryParams": [],
    "requestBodyType": null,
    "responseType": "Map"
  }
]
```

---

### `POST /apiscope/api/admin/reindex`

Forces a full re-embed of all scanned endpoints into the vector store. Deletes the existing vector store file and re-runs ingestion.

```bash
curl -X POST http://localhost:8080/apiscope/api/admin/reindex
```

**Response — 202 Accepted** (no body)

---

## Metrics

Handled by `EndpointMetricsController`. Only registered when `spring-boot-starter-actuator` is on the classpath.

---

### `GET /apiscope/api/endpoint-metrics?uri=...&method=...`

Proxies Micrometer's `http.server.requests` metric for a specific endpoint to the UI.

```bash
curl "http://localhost:8080/apiscope/api/endpoint-metrics?uri=/api/v1/subscriptions/{id}&method=GET"
```

**Response — 200 OK**

```json
{
  "avgResponseMs": 24.3,
  "successRate": 99.1,
  "totalRequests": 412,
  "available": true
}
```

Returns `{ "available": false }` when no data exists for the given URI + method.

---

## Flow Tracer

Handled by `FlowController` (in `apiscope-flow`). Only active when `apiscope.flow.enabled=true`.

---

### `POST /apiscope/api/flow/execute`

Starts an async traced execution. Returns the `traceId` immediately so the browser can open the SSE stream before execution completes.

**Request body:**
```json
{
  "httpMethod": "POST",
  "path": "/api/v1/subscriptions/{id}/terminate",
  "pathParams": { "id": "sub-123" },
  "queryParams": {},
  "body": "{ \"refundType\": \"PARTIAL\" }",
  "authorizationHeader": null
}
```

**Response — 202 Accepted**
```json
{ "traceId": "550e8400-e29b-41d4-a716-446655440000" }
```

---

### `GET /apiscope/api/flow/trace/{traceId}`

SSE stream of trace events for the given execution.

**Response — `text/event-stream`**

| Event name | Payload | Meaning |
|---|---|---|
| `step` | `TraceEvent` JSON | One intercepted method call |
| `done` | `FlowDoneEvent` JSON | Final HTTP response + total time |
| `error` | `{ "message": "..." }` | Network or fatal error |

**`TraceEvent` fields:** `traceId`, `stepIndex`, `layer` (CONTROLLER/SERVICE/REPOSITORY), `className`, `methodName`, `inputJson`, `outputJson`, `durationMs`, `status` (EXIT/ERROR), `errorMessage`, `sqlQueries`

**`FlowDoneEvent` fields:** `traceId`, `httpStatus`, `responseBody`, `totalMs`, `ok`, `stepCount`

---

## Static UI Resources

Served by Spring Boot's `ResourceHttpRequestHandler`.

| URL | Description |
|---|---|
| `GET /apiscope/` | React SPA entry point (index.html) |
| `GET /apiscope/index.html` | Same as above |
| `GET /apiscope/assets/*.js` | Vite-built JavaScript bundle |
| `GET /apiscope/assets/*.css` | Vite-built CSS bundle |

---

## Internal Data Contracts

### `ChatRequest`
```java
public record ChatRequest(@NotBlank String question) {}
```

### `ChatResponse`
```java
public record ChatResponse(String answer) {}
```

### `ApiEndpointMetadata`
```java
public record ApiEndpointMetadata(
    String path,
    String httpMethod,
    String controllerName,
    String methodName,
    String description,
    List<String> pathParams,
    List<String> requiredQueryParams,
    List<String> optionalQueryParams,
    String requestBodyType,   // null if no @RequestBody
    String responseType       // unwrapped from ResponseEntity<T>
) {}
```

---

## `curl` Examples

```bash
# Ask a question
curl -X POST http://localhost:8080/apiscope/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I process a payment?"}'

# Stream the answer token by token
curl -X POST http://localhost:8080/apiscope/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I cancel a subscription with a partial refund?"}'

# List all scanned endpoints
curl http://localhost:8080/apiscope/api/endpoints

# Force re-embed after adding new controllers
curl -X POST http://localhost:8080/apiscope/api/admin/reindex

# Trace a live API call
curl -X POST http://localhost:8080/apiscope/api/flow/execute \
  -H "Content-Type: application/json" \
  -d '{"httpMethod":"GET","path":"/api/v1/subscriptions/{id}","pathParams":{"id":"sub-123"}}'
```
