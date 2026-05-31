# 20 — Bug Fixes & Improvements (May 2026)

This document covers all bugs found and fixed across the APIScope backend and frontend during the May 2026 review session.

---

## Summary

| # | Area | Severity | Title |
|---|---|---|---|
| 1 | Backend — Flow | 🔴 High | `FlowDoneEvent.stepCount` always `0` |
| 2 | Backend — Flow | 🟠 Medium | Memory leak in `FlowAspect.stepCounters` |
| 3 | Backend — Scanner | 🟠 Medium | `FlowController` endpoints polluting the API list |
| 4 | Frontend — Dev | 🟡 Low | Vite dev-proxy missing `/api` prefix |
| 5 | Backend + Frontend | 🔴 High | Query params missing — all `?param` endpoints returned 400 |
| 6 | Frontend — Display | 🟡 Low | Query param labels showed `param0`, `param1` instead of real names |
| 7 | Frontend — UX | 🟡 Low | Optional params (with defaults) shown as required input fields |

---

## Fix 1 — `FlowDoneEvent.stepCount` always `0`

**Files changed:**
- `apiscope-flow/.../aspect/FlowAspect.java`
- `apiscope-flow/.../executor/FlowExecutorService.java`

**Problem:**  
The Flow Tracer UI always showed `"0 methods traced"` in the final response card. `FlowExecutorService` was hardcoding `0` as the `stepCount` when constructing `FlowDoneEvent`:

```java
// Before — hardcoded
sink.pushDone(traceId, new FlowDoneEvent(traceId, status, body, totalMs, ok, 0));
```

`FlowAspect` tracked step counters in a `ConcurrentHashMap<String, AtomicInteger>` but never exposed them externally.

**Fix:**  
Added a `getAndClearStepCount(traceId)` public method to `FlowAspect` that both reads the counter and removes the map entry. Injected `FlowAspect` into `FlowExecutorService` and called it just before pushing `done`:

```java
// After — real count
int stepCount = flowAspect.getAndClearStepCount(traceId);
sink.pushDone(traceId, new FlowDoneEvent(traceId, status, body, totalMs, ok, stepCount));
```

---

## Fix 2 — Memory Leak in `FlowAspect.stepCounters`

**Files changed:**
- `apiscope-flow/.../aspect/FlowAspect.java`

**Problem:**  
The `ConcurrentHashMap<String, AtomicInteger> stepCounters` inside `FlowAspect` was never cleaned up. Every trace execution added a new entry. Over time (especially in long-running apps or under load testing) this map would grow without bound, leaking memory.

**Fix:**  
The `getAndClearStepCount(traceId)` method added in Fix 1 uses `stepCounters.remove(traceId)` which both reads the final value and removes the entry atomically. This means the cleanup happens at the natural end of every trace with zero extra overhead.

```java
public int getAndClearStepCount(String traceId) {
    AtomicInteger counter = stepCounters.remove(traceId); // removes AND reads
    return counter != null ? counter.get() : 0;
}
```

---

## Fix 3 — `FlowController` Endpoints Polluting the API List

**Files changed:**
- `apiscope-core/.../scanner/ApiMetadataScanner.java`

**Problem:**  
`ApiMetadataScanner` had an `INTERNAL_PACKAGE_PREFIXES` exclusion list but it was missing `com.apiscope.flow`. This caused the Flow Tracer's own backend endpoints (`/apiscope/api/flow/execute`, `/apiscope/api/flow/trace/{traceId}`) to appear in:

- The **API Explorer** endpoint list
- The **Flow Tracer** endpoint dropdown
- The **AI Chat** context (the LLM was given information about internal infrastructure endpoints)

**Fix:**

```java
// Before
private static final List<String> INTERNAL_PACKAGE_PREFIXES = List.of(
    "com.apiscope.core",
    "com.apiscope.autoconfigure"
);

// After
private static final List<String> INTERNAL_PACKAGE_PREFIXES = List.of(
    "com.apiscope.core",
    "com.apiscope.autoconfigure",
    "com.apiscope.flow"           // ← added
);
```

---

## Fix 4 — Vite Dev-Proxy Missing `/api` Prefix

**Files changed:**
- `apiscope-ui/vite.config.js`

**Problem:**  
The Vite dev server proxy only forwarded `/apiscope/api/*` to `localhost:8080`. However, `tryItApi.js` (the "Try it out" panel) and `warmupApi.js` (metrics warmup) both make calls directly to `/api/v1/...` (the sample app endpoints). Those calls were **not proxied**, causing network errors in development mode — the browser was trying to reach a non-existent server on port 5173.

**Fix:**

```js
// Before
proxy: {
  '/apiscope/api': { target: 'http://localhost:8080', changeOrigin: true },
}

// After
proxy: {
  '/apiscope/api': { target: 'http://localhost:8080', changeOrigin: true },
  '/api':          { target: 'http://localhost:8080', changeOrigin: true }, // ← added
}
```

---

## Fix 5 — Query Parameters Missing Across the Entire Stack (400 Errors)

**Files changed:**
- `apiscope-ui/src/api/tryItApi.js`
- `apiscope-ui/src/hooks/useTryIt.js`
- `apiscope-ui/src/components/TryItPanel.jsx`
- `apiscope-ui/src/components/FlowTracer.jsx`
- `apiscope-flow/.../model/FlowRequest.java`
- `apiscope-flow/.../url/FlowUrlBuilder.java`

**Problem:**  
Endpoints with `@RequestParam` parameters (e.g. `GET /api/v1/analytics/revenue?fromDate=...&toDate=...`) **always returned HTTP 400** because query parameters were never sent. The entire `?key=value` pipeline was missing across all layers:

| Layer | What was missing |
|---|---|
| `tryItApi.js` | Never built a query string — URL was always just the path |
| `useTryIt.js` | No `queryParams` state existed |
| `TryItPanel.jsx` | No input fields for query params |
| `FlowTracer.jsx` | No query param inputs, never passed them to `send()` |
| `FlowRequest.java` | Record had no `queryParams` field — JSON was silently discarded |
| `FlowUrlBuilder.java` | Only substituted `{pathParam}` tokens, never appended `?key=value` |

**Fix — Frontend (`tryItApi.js`):**

```js
// Append query parameters to the URL before fetching
if (queryParams && Object.keys(queryParams).length > 0) {
  const qs = new URLSearchParams(
    Object.entries(queryParams).filter(([, v]) => v !== '' && v != null)
  ).toString()
  if (qs) url = `${url}?${qs}`
}
```

**Fix — Backend (`FlowRequest.java`):**

```java
// Before
public record FlowRequest(String httpMethod, String path,
                           Map<String, String> pathParams, String body) {}

// After
public record FlowRequest(String httpMethod, String path,
                           Map<String, String> pathParams,
                           Map<String, String> queryParams,  // ← added
                           String body) {}
```

**Fix — Backend (`FlowUrlBuilder.java`):**  
Replaced manual string concatenation with `UriComponentsBuilder` to safely encode and append query params:

```java
UriComponentsBuilder uriBuilder = UriComponentsBuilder
    .fromHttpUrl("http://localhost:" + serverPort + path);

if (request.queryParams() != null) {
    request.queryParams().forEach((key, value) -> {
        if (value != null && !value.isBlank()) {
            uriBuilder.queryParam(key, value);
        }
    });
}
return uriBuilder.toUriString();
```

---

## Fix 6 — Query Param Labels Showing `param0`, `param1`

**Files changed:**
- `apiscope-core/.../scanner/ApiMetadataScanner.java`

**Problem:**  
The API Explorer and Flow Tracer showed `?param0`, `?param1`, `?param2`, `?param3` instead of the real parameter names (`?fromDate`, `?toDate`, `?groupBy`, `?currency`).

**Root cause:**  
`MethodParameter.getParameterName()` returns `null` unless `initParameterNameDiscovery()` has been called. Without it the code fell straight to the `"param" + index` fallback.

**Fix:**  
A 4-level resolution chain now resolves param names reliably:

```java
// 1. Explicit annotation value:  @RequestParam("fromDate")
String value = (String) type.getMethod("value").invoke(ann);
if (value != null && !value.isBlank()) return value;

// 2. Explicit annotation name:  @RequestParam(name = "fromDate")
String name = (String) type.getMethod("name").invoke(ann);
if (name != null && !name.isBlank()) return name;

// 3. Java reflect Parameter.getName()
//    Works because pom.xml compiles with <parameters>true</parameters>
String reflectName = params[idx].getName();
if (reflectName != null && !reflectName.startsWith("arg")) return reflectName;

// 4. Spring DefaultParameterNameDiscoverer (fallback)
mp.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
String discovered = mp.getParameterName();
return discovered != null ? discovered : "param" + mp.getParameterIndex();
```

The `arg` guard in step 3 prevents returning the compiler-generated `arg0`, `arg1` names that appear for classes compiled *without* `-parameters`.

---

## Fix 7 — Optional Params Shown as Required Input Fields

**Files changed:**
- `apiscope-core/.../scanner/ApiEndpointMetadata.java`
- `apiscope-core/.../scanner/ApiMetadataScanner.java`
- `apiscope-ui/src/components/TryItPanel.jsx`
- `apiscope-ui/src/components/EndpointRow.jsx`
- `apiscope-ui/src/components/FlowTracer.jsx`

**Problem:**  
All `@RequestParam`s were treated identically. For `GET /api/v1/analytics/revenue`:

```java
@GetMapping("/revenue")
public ResponseEntity<?> getRevenue(
    @RequestParam String fromDate,          // required — no default
    @RequestParam String toDate,            // required — no default
    @RequestParam(defaultValue = "DAY") String groupBy,      // optional
    @RequestParam(defaultValue = "USD") String currency) {   // optional
```

The frontend showed all four as identical input fields labelled `?param0` through `?param3`. Users had no way to know which ones were required.

**Fix — Backend:**  
Split `queryParams: List<String>` into two separate fields in `ApiEndpointMetadata`:

```java
// Before
List<String> queryParams

// After
List<String> requiredQueryParams,   // required=true AND no defaultValue
List<String> optionalQueryParams    // required=false OR has defaultValue
```

`ApiMetadataScanner` determines which list a param belongs to:

```java
private boolean isRequiredRequestParam(MethodParameter mp) {
    RequestParam ann = mp.getParameterAnnotation(RequestParam.class);
    String def = ann.defaultValue();
    boolean hasDefault = !def.equals(ValueConstants.DEFAULT_NONE);
    return ann.required() && !hasDefault;
}
```

**Fix — Frontend (`TryItPanel`):**  
- **Required params** → shown as prominent sky-blue input fields with `* required` label
- **Optional params** → collapsed under a `▸ Optional Parameters (groupBy, currency)` toggle; expand to reveal inputs

**Fix — Frontend (`EndpointRow` — API Explorer info panel):**  
- **Required params** → sky-blue `?fromDate` `?toDate` badges  
- **Optional params** → muted slate-grey `?groupBy` `?currency` badges

**Fix — Frontend (`FlowTracer`):**  
Uses `requiredQueryParams` for input fields (matching TryItPanel behaviour).

---

## Live Preview URL Fix

**Files changed:**
- `apiscope-ui/src/components/TryItPanel.jsx`

The URL preview above the **Execute** button in `TryItPanel` previously showed only the static path (e.g. `/api/v1/analytics/revenue`) even after filling in query params. It now live-updates as you type:

```
/api/v1/analytics/revenue?fromDate=2026-01-01&toDate=2026-04-30
```

---

## Path Param Label Consistency Fix

**Files changed:**
- `apiscope-ui/src/components/TryItPanel.jsx`

`TryItPanel` was labelling path params as bare `userId` while `FlowTracer` correctly showed `{userId}`. Made consistent — both now display `{userId}`.
