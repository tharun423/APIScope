# 12 — Bug Fixes

## Overview

This document records the bugs identified during code review and the fixes applied.

---

## Fix 1 — Scanner Self-Indexing (RAG Pollution)

**File:** `agentic-docs-core/.../scanner/ApiMetadataScanner.java`  
**Severity:** Medium

**Problem:**  
`ApiMetadataScanner` was indexing its own internal endpoint (`POST /agentic-docs/api/chat`) into the vector store. This caused the LLM to occasionally surface the internal chat endpoint as if it were a user-facing API.

**Fix:**  
Added a filter to skip any `@RestController` bean whose class name starts with `com.agentic.docs`.

```java
if (beanType.getName().startsWith(SELF_PACKAGE_PREFIX)) {
    continue;
}
```

---

## Fix 2 — Thread-Safety in Scanner Duplicate Guard

**File:** `agentic-docs-core/.../scanner/ApiMetadataScanner.java`  
**Severity:** Low

**Problem:**  
The duplicate-event guard checked `scannedEndpoints.isEmpty()` on a non-`volatile` field. The JVM is free to cache the value per-thread, meaning the guard could fail under concurrent context refresh events.

**Fix:**  
Marked the field `volatile`:

```java
private volatile List<ApiEndpointMetadata> scannedEndpoints = Collections.emptyList();
```

---

## Fix 3 — Missing Input Validation in Chat Controller

**File:** `agentic-docs-core/.../chat/AgenticDocsChatController.java`  
**Severity:** Medium

**Problem:**  
A blank or `null` `question` field in the request body was passed directly to the vector store and LLM with no validation, wasting API calls and potentially causing errors.

**Fix:**  
Added an early return with `400 Bad Request` for empty questions:

```java
if (request.question() == null || request.question().isBlank()) {
    return ResponseEntity.badRequest()
            .body(new ChatResponse("Please provide a non-empty question."));
}
```

---

## Fix 4 — Null LLM Response Not Handled

**File:** `agentic-docs-core/.../chat/AgenticDocsChatController.java`  
**Severity:** Medium

**Problem:**  
`ChatClient.content()` can return `null` when the model returns an empty response (e.g., safety filters, timeouts). This `null` was returned directly to the frontend, causing a JSON serialisation issue and a broken UI state.

**Fix:**  
Added a null/blank guard with a sensible fallback:

```java
if (answer == null || answer.isBlank()) {
    answer = "I could not find a relevant endpoint for that. Please check the Swagger UI.";
}
```

The return type was also updated from `ChatResponse` to `ResponseEntity<ChatResponse>` to properly communicate HTTP status codes.

---

## Fix 5 — Broken Vertical Centering on Suggestions Screen

**File:** `agentic-docs-ui/src/App.jsx`  
**Severity:** Low

**Problem:**  
`SuggestionChips` used `flex-1` to fill available height for vertical centering, but its parent `<main>` element was not a flex container. This caused the suggestions panel to not be vertically centred on taller screens.

**Fix:**  
Added `flex flex-col` to the `<main>` element:

```jsx
<main className="flex flex-col flex-1 overflow-y-auto">
```

---

## Fix 6 — Deprecated `inline` Prop in react-markdown

**File:** `agentic-docs-ui/src/App.jsx`  
**Severity:** Low

**Problem:**  
The `code` component renderer in `react-markdown` v9 removed the `inline` prop. Using it caused a React warning and would break with future versions of the library.

**Fix:**  
Replaced the `inline` prop with a node-based heuristic that detects inline vs block code by checking for embedded newlines:

```jsx
code({ node, children, ...props }) {
  const isInline = node?.position?.start?.line === node?.position?.end?.line
    && !String(children).includes('\n');
  return isInline ? <code ...> : <div ...>
}
```

---

## Summary

| # | File | Issue | Severity |
|---|------|-------|----------|
| 1 | `ApiMetadataScanner` | Own endpoint indexed into RAG vector store | Medium |
| 2 | `ApiMetadataScanner` | Non-volatile field makes duplicate guard unreliable | Low |
| 3 | `AgenticDocsChatController` | No validation on blank/null question input | Medium |
| 4 | `AgenticDocsChatController` | Null LLM response not handled | Medium |
| 5 | `App.jsx` | `<main>` not a flex container, breaking vertical centering | Low |
| 6 | `App.jsx` | Deprecated `inline` prop in react-markdown code renderer | Low |
| 7 | `VectorStoreConfig` | Bean conflict with starter's auto-configured VectorStore | Medium |
| 8 | `AgenticDocsAutoConfiguration` | Required explicit `agentic.docs.enabled=true` to activate | Low |
| 9 | Parent `pom.xml` | Spring AI BOM not managing artifact versions correctly | High |
| 10 | Sample app `pom.xml` | Empty Maven profiles adding unnecessary complexity | Low |

---

## Fix 7 — VectorStore Bean Conflict

**File:** `agentic-docs-core/.../config/VectorStoreConfig.java`  
**Severity:** Medium

**Problem:**  
The `VectorStoreConfig` always created a `SimpleVectorStore` bean, even when `spring-ai-starter-vector-store-simple` in the starter already auto-configured one. This could cause `BeanDefinitionOverrideException` or unexpected behavior depending on bean ordering.

**Fix:**  
Added `@ConditionalOnMissingBean(VectorStore.class)` so the bean is only created as a fallback:

```java
@Bean
@ConditionalOnMissingBean(VectorStore.class)
public VectorStore vectorStore(EmbeddingModel embeddingModel) {
    return SimpleVectorStore.builder(embeddingModel).build();
}
```

---

## Fix 8 — AutoConfiguration Requires Explicit Opt-In

**File:** `agentic-docs-spring-boot-starter/.../AgenticDocsAutoConfiguration.java`  
**Severity:** Low

**Problem:**  
`@ConditionalOnProperty(havingValue = "true")` without `matchIfMissing = true` meant the starter did nothing unless users explicitly added `agentic.docs.enabled=true`. This violated the principle of least surprise for a starter library.

**Fix:**  
Added `matchIfMissing = true` so the starter activates by default. Users can still disable with `agentic.docs.enabled=false`.

---

## Fix 9 — Spring AI Dependency Versions Not Resolving

**File:** Parent `pom.xml`  
**Severity:** High

**Problem:**  
The `spring-ai-bom` import did not manage all Spring AI artifact versions (e.g., `spring-ai-core`, `spring-ai-starter-vector-store-simple`). This caused `'dependencies.dependency.version' is missing` build failures.

**Fix:**  
Added explicit version entries in `<dependencyManagement>` for all Spring AI artifacts used:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-core</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
```

---

## Fix 10 — Removed Unused Placeholder Packages and Empty Profiles

**Severity:** Low

**Problem:**  
Empty `rag/` and `web/` packages in the core module (with only `.gitkeep`) added confusion about the architecture. Empty Maven profiles in the sample app did nothing.

**Fix:**  
Removed the empty directories and stripped the redundant profiles from the sample app POM.
