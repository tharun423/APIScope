# 16 — Ollama Streaming & Performance Improvements

## Overview

When using a local Ollama model, the original implementation waited for the **entire LLM response** to be generated before sending anything back to the UI. On typical laptop hardware this means staring at a spinner for 10–30 seconds per question.

This document describes the two-pronged fix applied:

1. **Server-Sent Events (SSE) streaming** — the UI receives and renders tokens as the model generates them, so the first word appears within ~1 second.
2. **Ollama inference tuning** — configuration changes that reduce per-token overhead and eliminate cold-start delays.

---

## Changes at a Glance

| File | What Changed |
|---|---|
| `StreamingChatService.java` | New interface extending `ChatService` with `streamAnswer()` — fixes ISP/OCP/LSP |
| `AgenticDocsChatService.java` | Now implements `StreamingChatService` instead of `ChatService` directly |
| `AgenticDocsChatController.java` | New `POST /agentic-docs/api/chat/stream` SSE endpoint; `instanceof StreamingChatService` pattern (no concrete cast) |
| `chatApi.js` | New `sendChatMessageStream()` function using Fetch + ReadableStream |
| `useChat.js` | Rewired to use streaming; builds assistant message token by token |
| `AiChat.jsx` | Typing indicator only shown until first token arrives |
| `application-ollama.properties` | `keep-alive`, `num-ctx`, `num-predict`, `temperature` tuned |

---

## Backend Changes

### New method — `AgenticDocsChatService.streamAnswer()`

```java
public Flux<String> streamAnswer(ChatRequest request) {
    List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.builder()
                    .query(request.question())
                    .topK(properties.topK())
                    .build()
    );

    String context = relevantDocs.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n---\n"));

    String systemPrompt = (properties.systemPrompt() != null && !properties.systemPrompt().isBlank())
            ? properties.systemPrompt()
            : DEFAULT_SYSTEM_PROMPT;

    return chatClient.prompt()
            .system(s -> s.text(systemPrompt).param("context", context))
            .user(request.question())
            .stream()
            .content();
}
```

The RAG pipeline (vector search + context building) is identical to the blocking `answer()` method. The only difference is `.stream().content()` instead of `.call().content()`, which returns a reactive `Flux<String>` that emits one token string at a time.

The original `POST /agentic-docs/api/chat` endpoint is **unchanged** — it still works for any client that prefers a single JSON response.

---

### New endpoint — `POST /agentic-docs/api/chat/stream`

```
POST /agentic-docs/api/chat/stream
Content-Type: application/json

{ "question": "How do I create a user?" }
```

**Response:** `text/event-stream` (Server-Sent Events)

Each SSE event uses a named event type:

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

The endpoint uses `SseEmitter` with a **3-minute timeout** (`180_000 ms`) to accommodate slow hardware on the first request after startup.

If the injected `ChatService` is a custom implementation (not `AgenticDocsChatService`), the endpoint automatically falls back to the blocking `answer()` call and emits the full response as a single `token` event followed by `done`. This preserves full backward compatibility.

---

## Frontend Changes

### `src/api/chatApi.js` — `sendChatMessageStream()`

```js
sendChatMessageStream(question, onToken, onDone, onError)
```

| Parameter | Type | Description |
|---|---|---|
| `question` | `string` | The user's question |
| `onToken` | `(token: string) => void` | Called for each token received |
| `onDone` | `() => void` | Called when the stream completes |
| `onError` | `(message: string) => void` | Called on network or server error |
| **returns** | `() => void` | Call to abort the in-flight request |

Internally uses the browser's `fetch()` API with a `ReadableStream` reader and a `TextDecoder` to parse SSE lines incrementally.

The original `sendChatMessage()` function is **unchanged** and still exported for use in tests or custom implementations.

---

### `src/hooks/useChat.js` — Token-by-token state updates

The hook pre-inserts an empty assistant message placeholder as soon as the user sends a question, then patches it on every `onToken` callback:

```js
// onToken — append each token to the last (assistant) message
(token) => {
  setMessages((prev) => {
    const updated = [...prev]
    const last    = updated[updated.length - 1]
    updated[updated.length - 1] = { ...last, content: last.content + token }
    return updated
  })
}
```

This triggers a React re-render per token, so text appears progressively in the UI just like ChatGPT.

---

### `src/components/AiChat.jsx` — Smarter typing indicator

```js
const waitingForFirstToken = loading && lastMsg?.role === 'assistant' && !lastMsg?.content
```

The `<TypingIndicator />` is only rendered while `loading` is true **and** the assistant placeholder is still empty. The moment the first token arrives and text starts flowing, the indicator disappears — avoiding a confusing flash where the spinner and text appear simultaneously.

---

## Ollama Configuration Tuning

File: `agentic-docs-sample-app/src/main/resources/application-ollama.properties`

### `keep-alive=-1` (was `5m`)

```properties
spring.ai.ollama.chat.options.keep-alive=-1
```

Keeps the model weights loaded in RAM **indefinitely** instead of unloading them after 5 minutes of idle time.

- **Before:** First request after 5+ minutes of idle → 10–30 second cold-start while Ollama loads `llama3.2` (~2 GB) into memory.
- **After:** Every request gets a warm model — no cold-start delay at all.
- **Trade-off:** ~2 GB of RAM is permanently occupied. If RAM is constrained, change back to `10m` or `30m`.

---

### `num-ctx=2048` (was default 4096)

```properties
spring.ai.ollama.chat.options.num-ctx=2048
```

Sets the context window (KV cache) to 2048 tokens instead of the model default of 4096.

- The RAG context injected by Agentic Docs (top-5 API endpoint descriptions) is typically 200–500 tokens. A 2048-token window is more than sufficient.
- Halving the context window roughly halves the memory bandwidth and attention computation cost per token, translating to faster token generation.

---

### `num-predict=512` (was unlimited)

```properties
spring.ai.ollama.chat.options.num-predict=512
```

Caps the maximum number of tokens the model will generate per response at 512 (~350 words).

- API documentation answers — endpoint explanations, code snippets, parameter lists — rarely exceed 300 words.
- Without a cap, the model can generate thousands of tokens on open-ended questions, making the user wait unnecessarily.

---

### `temperature=0.1` (was default 0.8)

```properties
spring.ai.ollama.chat.options.temperature=0.1
```

Reduces randomness in token selection. At `0.1` the model almost always picks the highest-probability next token, which:

- Makes answers more consistent and factual (important for API documentation).
- Marginally speeds up token selection by reducing the entropy calculation.

---

## End-to-End Flow After These Changes

```
User types question → React UI
        │
        ▼
POST /agentic-docs/api/chat/stream
        │
        ▼
AgenticDocsChatController.chatStream()
        │
        ├── vectorStore.similaritySearch(topK=5)   ← same as before
        ├── Builds context string                   ← same as before
        │
        └── AgenticDocsChatService.streamAnswer()
                │
                └── chatClient.stream().content()
                        │
                        ├── token "Use"      → SSE event → UI appends "Use"
                        ├── token " POST"    → SSE event → UI appends " POST"
                        ├── token " /api..." → SSE event → UI appends " /api..."
                        │   (... more tokens ...)
                        └── [DONE]           → SSE done  → loading=false
```

**Perceived latency comparison:**

| Scenario | Before | After |
|---|---|---|
| Cold start (model not loaded) | 10–30 s wait, then full answer | 10–30 s wait, then tokens stream in |
| Warm model (keep-alive active) | 5–15 s wait, then full answer | First token in ~1 s, text streams in |
| After idle > keep-alive timeout | Cold start every time | Never — model stays loaded |

---

## Backward Compatibility

- `POST /agentic-docs/api/chat` (original endpoint) is **untouched** — any existing client continues to work exactly as before.
- Custom `ChatService` implementations injected via `@Primary @Bean` are fully supported on the stream endpoint — if they do not implement `StreamingChatService`, they automatically fall back to the blocking path with a single-event SSE response.
- The non-streaming `sendChatMessage()` in `chatApi.js` is still exported and usable.
