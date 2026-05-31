# 08 — API Reference

## Chat Endpoint

The only public API endpoint exposed by Agentic Docs.

---

### `POST /agentic-docs/api/chat`

Accepts a natural language question, performs RAG retrieval against the indexed endpoints, and returns an LLM-generated answer.

#### Request

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "question": "string"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `question` | `string` | Yes | The natural language question from the developer |

**Example:**
```json
{
  "question": "How do I cancel a subscription with a partial refund?"
}
```

---

#### Response

**Status:** `200 OK`

**Body:**
```json
{
  "answer": "string"
}
```

| Field | Type | Description |
|---|---|---|
| `answer` | `string` | The LLM-generated answer, formatted as Markdown |

**Example:**
```json
{
  "answer": "To cancel a subscription with a partial refund, you need to call the **Termination API**.\n\n## Steps\n\n1. First, check the subscription's `daysActive` field:\n\n```java\nSubscription sub = subscriptionClient.getDetails(subId);\nboolean isEligible = sub.getDaysActive() < 15;\n```\n\n2. Then call `POST /api/v1/subscriptions/{id}/terminate` with:\n\n```json\n{\n  \"refundType\": \"PARTIAL\",\n  \"isProrated\": true\n}\n```"
}
```

---

#### Error Responses

| Status | Cause | Body |
|---|---|---|
| `400 Bad Request` | Missing or blank `question` field | `{ "answer": "Please provide a non-empty question." }` |
| `429 Too Many Requests` | Rate limit exceeded (20 req/min per IP by default) | `{ "answer": "Rate limit exceeded. Please slow down." }` |
| `405 Method Not Allowed` | GET request to `/chat` endpoint | `{ "answer": "This endpoint only accepts POST requests..." }` |
| `500 Internal Server Error` | LLM API failure or vector store error | Spring default error body |

---

#### CORS

CORS is configured via `AgenticDocsMvcConfigurer` using the `agentic.docs.cors.allowed-origins` property (default: `http://localhost:5173`). Unlike the previous `@CrossOrigin(origins = "*")` annotation, this is configurable without recompiling.

```properties
# application.properties — allow multiple origins
agentic.docs.cors.allowed-origins=http://localhost:5173,https://yourapp.com
```

---

## Example `curl` Calls

### Basic question
```bash
curl -X POST http://localhost:8080/agentic-docs/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What endpoints are available?"}'
```

### Code generation request
```bash
curl -X POST http://localhost:8080/agentic-docs/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Generate Java code to process a payment"}'
```

### Multi-step workflow question
```bash
curl -X POST http://localhost:8080/agentic-docs/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I cancel a premium subscription but only trigger a partial refund if the user has been active for less than 15 days?"}'
```

---

---

### `POST /agentic-docs/api/chat/stream`

Same RAG pipeline as the blocking endpoint, but delivers the LLM response token-by-token via **Server-Sent Events (SSE)**. This eliminates the long wait when using a local Ollama model.

#### Request

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "question": "string"
}
```

#### Response

**Content-Type:** `text/event-stream`

Each SSE event has a named `event` field:

| Event name | Data | Meaning |
|---|---|---|
| `token` | raw token text | One piece of the LLM's answer |
| `done` | `[DONE]` | Stream has completed normally |
| `error` | error message | Something went wrong |

**Example stream:**
```
event: token
data: Use

event: token
data:  POST

event: token
data:  /api/users

event: done
data: [DONE]
```

The endpoint has a **3-minute SSE timeout** (`180,000 ms`) to accommodate slow first-request latency on lower-end hardware.

Client disconnects automatically cancel the upstream `Flux` subscription, stopping Ollama token generation immediately.

---

### `GET /agentic-docs/api/chat`

Returns `405 Method Not Allowed` with a usage hint. Useful when someone hits the endpoint in a browser or with a plain `curl` GET.

---

## Static UI Resources

These are served by Spring Boot's `ResourceHttpRequestHandler` — not REST endpoints.

| URL | Description |
|---|---|
| `GET /agentic-docs/` | React SPA entry point (index.html) |
| `GET /agentic-docs/index.html` | Same as above |
| `GET /agentic-docs/assets/*.js` | Vite-built JavaScript bundle |
| `GET /agentic-docs/assets/*.css` | Vite-built CSS bundle |

---

## Internal Data Contracts

These are not HTTP endpoints but document the internal Java records used by the chat controller.

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
    String path,              // e.g. "/api/v1/subscriptions/{id}"
    String httpMethod,        // e.g. "POST"
    String controllerName,    // e.g. "PaymentsController"
    String methodName,        // e.g. "terminateSubscription"
    String description,       // from @Operation(summary) or camelCase-to-sentence
    List<String> pathParams,  // e.g. ["id"]
    List<String> queryParams, // e.g. ["page", "size"]
    String requestBodyType,   // e.g. "CancelRequest" (null if none)
    String responseType       // e.g. "SubscriptionDto" (unwrapped from ResponseEntity<T>)
) {}
```

### `GET /agentic-docs/api/endpoints`

Returns the full list of scanned `ApiEndpointMetadata` as JSON. Used by the API Explorer panel in the React UI.

```bash
curl http://localhost:8080/agentic-docs/api/endpoints
```
