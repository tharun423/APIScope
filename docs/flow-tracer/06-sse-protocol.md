# 06 — SSE Protocol in Flow Tracer

## What Is Server-Sent Events?

Server-Sent Events (SSE) is a browser-native protocol for receiving a stream of
text events from a server over a single long-lived HTTP connection. Unlike
WebSockets, SSE is:

- **Unidirectional** — server pushes, client only listens
- **HTTP/1.1 compatible** — works through proxies and load balancers that understand
  chunked transfer encoding
- **Automatically reconnecting** — the browser retries the connection if it drops
- **Simple** — a plain text protocol, easy to debug with `curl`

---

## SSE Wire Format

Each event is a block of plain text lines followed by a blank line:

```
event: step\n
data: {"traceId":"abc","stepIndex":0,"layer":"CONTROLLER",...}\n
\n
event: step\n
data: {"traceId":"abc","stepIndex":1,"layer":"SERVICE",...}\n
\n
event: done\n
data: {"traceId":"abc","httpStatus":200,"responseBody":"{...}","totalMs":87,...}\n
\n
```

Spring's `SseEmitter.send(SseEmitter.event().name("step").data(json))` writes
exactly this format over the HTTP response stream.

---

## Event Types

### `step` Event

Sent for each `TraceEvent` as it is captured by the AOP advice.

```json
{
  "traceId":     "550e8400-e29b-41d4-a716-446655440000",
  "stepIndex":   0,
  "layer":       "CONTROLLER",
  "className":   "OrderFlowController",
  "methodName":  "checkout",
  "inputJson":   "{\"productId\":\"P001\",\"quantity\":2,...}",
  "outputJson":  "{\"orderId\":\"ORD-168D1D26\",...}",
  "durationMs":  42,
  "status":      "EXIT",
  "errorMessage": null
}
```

If the method threw an exception:
```json
{
  "status":      "ERROR",
  "errorMessage": "Insufficient stock for product P001",
  "outputJson":  null
}
```

### `done` Event

Sent once after the HTTP call to the target endpoint completes (success or 4xx/5xx).

```json
{
  "traceId":      "550e8400-e29b-41d4-a716-446655440000",
  "httpStatus":   200,
  "responseBody": "{\"orderId\":\"ORD-168D1D26\",\"status\":\"CONFIRMED\"}",
  "totalMs":      87,
  "ok":           true,
  "stepCount":    10
}
```

### `error` Event

Sent if the executor itself fails (e.g. cannot connect to the target host,
`RestClient` throws an exception unrelated to HTTP status).

```json
{
  "message": "Connection refused: localhost/127.0.0.1:8080"
}
```

---

## Server Side: Spring SseEmitter

Spring Boot's `SseEmitter` holds a response output stream open and allows
any thread to write events to it:

```java
// Controller — opens the SSE connection
@GetMapping("/trace/{traceId}")
public SseEmitter trace(@PathVariable String traceId) {
    return registry.attach(traceId);
}

// Registry — pushes an event from any thread (e.g. the AOP advice thread)
public void pushStep(String traceId, TraceEvent event) {
    TraceEntry entry = entries.get(traceId);
    if (entry == null) return;

    String json = objectMapper.writeValueAsString(event);
    BufferedEvent buffered = new BufferedEvent("step", json);
    entry.buffer.add(buffered);  // always buffer for replay

    if (entry.emitter != null) {
        try {
            entry.emitter.send(
                SseEmitter.event().name("step").data(json)
            );
        } catch (IOException e) {
            entry.emitter.completeWithError(e);
        }
    }
}
```

**Timeout:** `new SseEmitter(120_000L)` — the connection stays open for up to
120 seconds. If no `done` or `error` event arrives within that window, Spring
automatically closes the emitter and the browser reconnects (then gets nothing,
as the trace entry has been cleaned up).

---

## Client Side: EventSource API

```js
const es = new EventSource(`/agentic-docs/api/flow/trace/${traceId}`);

es.addEventListener('step', (e) => {
    const step = JSON.parse(e.data);
    onStep(step);
});

es.addEventListener('done', (e) => {
    const done = JSON.parse(e.data);
    onDone(done);
    es.close();  // ← important: close after done, prevents auto-reconnect
});

es.addEventListener('error', (e) => {
    onError(JSON.parse(e.data)?.message ?? 'Unknown error');
    es.close();
});

es.onerror = () => {
    onError('SSE connection lost');
    es.close();
};
```

> **Important:** Call `es.close()` after receiving `done` or `error`.
> If you don't, the browser will try to reconnect to the SSE endpoint, which
> will find no trace entry (it was cleaned up) and hang until timeout.

---

## Late-Connect Buffering

The sequence of events between the frontend and backend is:

```
1. Browser: POST /execute → receives { traceId }   (≈ 0ms)
2. Backend: virtual thread starts, fires HTTP call  (≈ 1-5ms)
3. Browser: GET /trace/{traceId}                   (≈ 2-10ms)
```

Between steps 2 and 3, the AOP advice may have already captured and pushed
several `step` events. If the browser hasn't connected yet, those events would
be lost without buffering.

`FlowSseRegistry` always appends to `entry.buffer` on every `pushStep()`,
regardless of whether an emitter is attached. When `attach()` is called later:

```java
public SseEmitter attach(String traceId) {
    SseEmitter emitter = new SseEmitter(120_000L);
    entry.emitter = emitter;

    // Replay all buffered events immediately
    for (BufferedEvent ev : entry.buffer) {
        emitter.send(SseEmitter.event().name(ev.eventName()).data(ev.data()));
    }

    return emitter;
}
```

This replay is synchronous — the browser receives all buffered events instantly
on connection, then continues to receive new events as they arrive.

---

## Cleanup

After a trace completes (either `done` or `error`), the registry removes the
trace entry:

```java
entries.remove(traceId);
```

This prevents memory accumulation. If a browser never opens the SSE connection
(e.g. the user navigated away), the trace entry stays in memory until the
JVM is restarted — a known limitation. A future improvement would add a
TTL (time-to-live) eviction using `ScheduledExecutorService`.

---

## Debugging SSE with curl

You can verify the SSE stream is working without the browser:

```bash
# 1. Trigger an execution
curl -s -X POST http://localhost:8080/agentic-docs/api/flow/execute \
  -H "Content-Type: application/json" \
  -d '{"httpMethod":"GET","path":"/api/v1/products","pathParams":{},"body":null}'

# → {"traceId":"550e8400-..."}

# 2. Subscribe to the trace (use the traceId from above)
curl -N http://localhost:8080/agentic-docs/api/flow/trace/550e8400-...

# You will see a stream of:
# event: step
# data: {...}
#
# event: done
# data: {...}
```
