# Design Review & Fixes — May 2026

This document records the design issues identified during a full-project review and the fixes applied.

---

## 1. VectorStoreConfig — Broken Shutdown Persistence (Critical)

**File:** `agentic-docs-core/src/main/java/com/agentic/docs/core/config/VectorStoreConfig.java`

**Problem:**  
The `saveOnShutdown` method was annotated with `@EventListener(ContextClosedEvent.class)` but declared two parameters (`SimpleVectorStore store, AgenticDocsProperties properties`). Spring's `@EventListener` methods receive only the **event object** as their parameter — not arbitrary beans. As a result, Spring could never invoke this method, and **the vector store was never saved to disk on shutdown**. All embeddings computed at startup were lost on every restart.

**Fix:**  
- Injected `ObjectProvider<SimpleVectorStore>` and `AgenticDocsProperties` via the constructor.
- Replaced `@EventListener(ContextClosedEvent.class)` with `@PreDestroy`, which Spring calls reliably during shutdown.
- Used `ObjectProvider.getIfAvailable()` to safely handle cases where no `SimpleVectorStore` bean exists (e.g., when a user provides a custom `VectorStore`).

---

## 2. ChatController — Redundant GET Mapping & Duplicate Validation

**File:** `agentic-docs-core/src/main/java/com/agentic/docs/core/chat/AgenticDocsChatController.java`

**Problem A:**  
An explicit `@GetMapping("/chat")` returned HTTP 405 manually. This is unnecessary — Spring automatically returns 405 Method Not Allowed for unmapped HTTP methods. The explicit mapping was confusing and could interfere with CORS preflight (`OPTIONS`) handling.

**Problem B:**  
The `POST /chat` handler manually checked `request.question() == null || request.question().isBlank()` despite the `ChatRequest` record already declaring `@NotBlank` on the `question` field, which is enforced by `@Validated`. The manual check was dead code.

**Fix:**  
- Removed the `@GetMapping("/chat")` method entirely.
- Removed the redundant `isBlank()` check in the `POST` handler.
- Removed the unused `HttpStatus` import.

---

## 3. RateLimiterService — Unbounded Memory Growth

**File:** `agentic-docs-core/src/main/java/com/agentic/docs/core/ratelimit/RateLimiterService.java`

**Problem:**  
The `ConcurrentHashMap<String, Bucket>` storing per-IP rate-limit buckets grew without bound. Every unique client IP created a permanent entry. In production with many clients (or under IP-spoofing attacks), this is a **memory leak**.

**Fix:**  
- Replaced `ConcurrentHashMap` with a `Collections.synchronizedMap` wrapping a `LinkedHashMap` configured as an LRU cache (access-order mode).
- Capped at 10,000 entries — the least-recently-used bucket is evicted when the limit is exceeded.
- This bounds memory usage while preserving rate-limiting accuracy for active clients.

---

## 4. ApiMetadataScanner — `/unknown` Path Pollution

**File:** `agentic-docs-core/src/main/java/com/agentic/docs/core/scanner/ApiMetadataScanner.java`

**Problem:**  
When an endpoint's path could not be resolved, the scanner returned `"/unknown"` as a fallback. This value was then ingested into the vector store, polluting the RAG context with meaningless data that could confuse the LLM.

**Fix:**  
- Added a `.filter(e -> !"/unknown".equals(e.path()))` to the scanning pipeline, silently skipping endpoints without a resolvable path.

---

## 5. useChat Hook — Stream Abort Leak

**File:** `agentic-docs-ui/src/hooks/useChat.js`

**Problem:**  
`sendChatMessageStream` returns an `abort` function, but `useChat` never stored or called it. If the user switched tabs or started a new chat mid-stream:
- The old stream continued consuming data in the background.
- React state updates were applied to an unmounted component, causing warnings and wasted resources.

**Fix:**  
- Store the abort function in a `useRef`.
- Call `abortRef.current?.()` before starting a new stream (cancels any in-flight request).
- Call `abortRef.current?.()` in a `useEffect` cleanup (aborts on unmount).
- Added `timestamp` field to each message object at creation time (see fix #6).

---

## 6. MessageBubble — Incorrect Timestamps

**File:** `agentic-docs-ui/src/components/MessageBubble.jsx`

**Problem:**  
The timestamp was computed as `new Date()` inside the render function. This means every re-render (including scrolling, streaming tokens, or window resizing) updated all message timestamps to the **current** time. Older messages appeared to have been sent "just now."

**Fix:**  
- `useChat.js` now stores a `timestamp: new Date().toISOString()` on each message when it is created.
- `MessageBubble.jsx` reads `msg.timestamp` and formats it, instead of creating a new `Date` at render time.

---

## Issues Identified But Not Yet Fixed

The following items were identified during the review and are candidates for future work:

| # | Area | Issue |
|---|------|-------|
| 1 | Backend | No `@ControllerAdvice` global exception handler — validation errors return Spring Boot's default error shape |
| 2 | Backend | 800-char internal truncation in `AgenticDocsChatService` contradicts the 2000-char `@Size` on `ChatRequest` |
| 3 | Backend | Sample app controllers return untyped `Map<>` — scanner reports `Map` as the response type, degrading RAG quality |
| 4 | Backend | Blocking `/chat` endpoint runs on the servlet thread — consider virtual threads or `@Async` |
| 5 | Backend | CORS origins config is a single `String` — should support multiple origins |
| 6 | Frontend | Per-row metrics polling (N endpoints = N fetch calls every 30s) — should batch into one call |
| 7 | Frontend | `TypingIndicator` injects a `<style>` tag on every render — move keyframes to CSS |
| 8 | Frontend | No accessibility attributes (`aria-live`, `role`, keyboard focus management) |
| 9 | Frontend | No frontend tests |
| 10 | Frontend | SSE parser assumes complete lines per chunk — fragile with network buffering |
| 11 | Backend | Missing tests for `RateLimiterService`, `RateLimitInterceptor`, `WebConfig`, `MetricsController`, `VectorStoreConfig` |
| 12 | Backend | `SimpleVectorStore` (file-backed JSON) is not production-ready — needs migration guidance for PGVector/Chroma |
