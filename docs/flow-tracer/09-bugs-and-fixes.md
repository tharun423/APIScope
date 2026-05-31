# 09 — Flow Tracer: Bugs and Fixes

This file documents every bug encountered during the implementation of Flow Tracer,
the root cause analysis, and the exact fix applied.

---

## Bug 1 — AOP Pointcut Excludes the Sample App (Steps Not Appearing)

### Symptom

After implementing `FlowAspect` and the full SSE pipeline, running a checkout
trace showed only the final response JSON — no step cards appeared in the
Flow Diagram.

### Root Cause

The `@Around` pointcut in `FlowAspect.java` had the exclusion:

```java
&& !within(com.agentic.docs..*)
```

The `..` wildcard in AspectJ means *this package and all sub-packages*. Since
the sample app classes live at:

```
com.agentic.docs.sample.controller.OrderFlowController
com.agentic.docs.sample.service.OrderService
com.agentic.docs.sample.repository.InventoryRepository
```

...all of which are under `com.agentic.docs.*`, the exclusion silently prevented
the AOP advice from ever firing on sample app beans.

The `X-Flow-Trace-Id` header was correctly added to the outbound request by
`FlowExecutorService`, and the SSE connection was established — but `FlowAspect`
returned immediately from `extractTraceId()` on every call without recording
any event, because the pointcut excluded those classes entirely.

### Fix

Change the exclusion from a blanket wildcard to two targeted exclusions:

```java
// BEFORE (wrong):
&& !within(com.agentic.docs..*)

// AFTER (correct):
&& !within(com.agentic.docs.flow..*)
&& !within(com.agentic.docs.core..*)
```

This keeps the following in scope (intentionally traced):
- `com.agentic.docs.sample.*` — the sample app controllers, services, repos

This excludes only the packages that would cause problems:
- `com.agentic.docs.flow.*` — the tracer itself (infinite recursion)
- `com.agentic.docs.core.*` — the framework core (metrics, chat, vector store)

### Files Changed

- `agentic-docs-flow/src/main/java/com/agentic/docs/flow/aspect/FlowAspect.java`

---

## Bug 2 — Step Cards Rendered in Wrong Order

### Symptom

Step cards appeared in the Flow Diagram in an unexpected order. The inner
calls (e.g. `InventoryRepository.findStockByProductId`) appeared before the
outer calls (e.g. `OrderFlowController.checkout`) that initiated them.

### Root Cause

Spring AOP's `@Around` advice captures the step index at *method entry* but
pushes the SSE event at *method exit* (after `proceed()` returns). For nested
calls, the sequence is:

```
Enter checkout()         → stepIndex=0 assigned
  Enter validateOrder()  → stepIndex=1 assigned
  Exit  validateOrder()  → pushStep(stepIndex=1) ← SSE fires first
  Enter checkAvail()     → stepIndex=2 assigned
  Exit  checkAvail()     → pushStep(stepIndex=2) ← SSE fires second
Exit  checkout()         → pushStep(stepIndex=0) ← SSE fires last
```

The browser receives steps in order: 1, 2, 0 — which is completion order,
not call order. React rendered them in arrival order, so the diagram showed
the inner calls before the outer call that triggered them.

### Fix

In `FlowTracer.jsx`, sort the steps array by `stepIndex` before rendering:

```jsx
// BEFORE:
{steps.map((step, i) => (
  <FlowStepCard key={step.stepIndex} step={step} index={i} />
))}

// AFTER:
{[...steps]
  .sort((a, b) => a.stepIndex - b.stepIndex)
  .map((step, i) => (
    <FlowStepCard key={step.stepIndex} step={step} index={i} />
  ))
}
```

`[...steps]` creates a copy to avoid mutating React state directly.
`.sort((a,b) => a.stepIndex - b.stepIndex)` restores call order.

### Files Changed

- `agentic-docs-ui/src/components/FlowTracer.jsx`

---

## Bug 3 — `@Operation` Import Causes Build Failure in Sample App

### Symptom

After adding `OrderFlowController.java` with `@Operation` annotations from
Swagger/OpenAPI, the build failed:

```
cannot find symbol
  symbol: class Operation
  location: class OrderFlowController
```

### Root Cause

`io.swagger.v3.oas.annotations.Operation` is provided by the `springdoc-openapi`
dependency. The `agentic-docs-sample-app` module did not have `springdoc-openapi`
on its compile classpath at the time the class was written — the annotation was
added by habit from other modules that do include springdoc.

### Fix

Remove the `@Operation` annotations and the import statement from
`OrderFlowController.java`. The endpoint is still discoverable via the standard
Spring MVC `@PostMapping` and `@RequestMapping` annotations (which springdoc
also reads when present).

```java
// REMOVED:
import io.swagger.v3.oas.annotations.Operation;

// REMOVED from each method:
@Operation(summary = "...")
```

### Files Changed

- `agentic-docs-sample-app/.../controller/OrderFlowController.java`

---

## Bug 4 — Javadoc `&` Ampersand Causes Build Failure

### Symptom

Running `mvn clean install` failed with:

```
error: illegal character in Javadoc: &
  * Filters endpoints by method & path
                                ^
```

### Root Cause

Java's Javadoc tool (since JDK 8+) treats the `&` character in documentation
comments as the start of an HTML entity. A bare `&` that is not followed by
a valid entity name (e.g. `&amp;`, `&lt;`) is a Javadoc error when using
strict HTML validation.

This was a pre-existing issue in several controllers — not introduced by
Flow Tracer — but surfaced when the full build was run with Javadoc generation.

Affected files:
- `agentic-docs-core/.../metrics/EndpointMetricsController.java`
- 7 controllers in `agentic-docs-sample-app`

### Fix

Replace bare `&` with `&amp;` in all Javadoc comments.

**Manual fix** (for `EndpointMetricsController.java`):
```java
// BEFORE:
* Filters endpoints by method & path

// AFTER:
* Filters endpoints by method &amp; path
```

**Automated fix** (for the 7 sample app controllers via PowerShell):
```powershell
Get-ChildItem -Path "agentic-docs-sample-app\src" -Recurse -Filter "*.java" |
ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    $fixed   = $content -replace '(?<=\* .*)&(?!amp;|lt;|gt;|quot;|apos;)', '&amp;'
    if ($content -ne $fixed) {
        Set-Content $_.FullName $fixed
        Write-Host "Fixed: $($_.FullName)"
    }
}
```

### Files Changed

- `agentic-docs-core/src/main/java/com/agentic/docs/core/metrics/EndpointMetricsController.java`
- `agentic-docs-sample-app/src/main/java/**/*Controller.java` (7 files)

---

## Bug 5 — Missing Maven Artifact `agentic-docs-flow:jar:1.0.0`

### Symptom

Running `mvn clean install` from the root pom failed with:

```
Could not resolve dependencies for project agentic-docs-spring-boot-starter:
  Could not find artifact com.agentic.docs:agentic-docs-flow:jar:1.0.0
```

### Root Cause

The `agentic-docs-flow` module was newly added to the root `pom.xml` and
referenced as a dependency in `agentic-docs-spring-boot-starter/pom.xml`.
However, the module had never been built or installed to the local Maven
repository (`.m2/repository`). Maven's multi-module reactor resolves
inter-module dependencies from the local repo unless you specify the module
build order correctly.

### Fix

Build and install the new module first, then run the full build:

```bash
# Step 1: Build and install agentic-docs-flow into local .m2
mvn clean install -DskipTests -pl agentic-docs-flow -am

# Step 2: Full build now succeeds
mvn clean install -DskipTests
```

The `-am` flag (also-make) ensures all upstream dependencies of `agentic-docs-flow`
are also built. After step 1, the JAR is in `~/.m2/repository/com/agentic/docs/agentic-docs-flow/1.0.0/`.

Alternatively, run `mvn clean install` from the root — Maven's reactor should
resolve the module order correctly as long as the `<module>` entries in the
root `pom.xml` are listed in dependency order:

```xml
<modules>
    <module>agentic-docs-core</module>
    <module>agentic-docs-flow</module>          <!-- before starter -->
    <module>agentic-docs-spring-boot-starter</module>
    <module>agentic-docs-sample-app</module>
</modules>
```

### Files Changed

- Root `pom.xml` — added `<module>agentic-docs-flow</module>` in correct position

---

## Summary Table

| # | Bug | Where | Impact | Status |
|---|-----|-------|--------|--------|
| 1 | AOP pointcut excludes sample app | `FlowAspect.java` | No steps visible | ✅ Fixed |
| 2 | Step cards in wrong order | `FlowTracer.jsx` | Confusing call chain | ✅ Fixed |
| 3 | `@Operation` import not on classpath | `OrderFlowController.java` | Build failure | ✅ Fixed |
| 4 | Bare `&` in Javadoc comments | Multiple controllers | Build failure | ✅ Fixed |
| 5 | `agentic-docs-flow` not in local `.m2` | Root `pom.xml` / build order | Build failure | ✅ Fixed |

---

## Refactor 6 — SOLID Principle Violations (Design Debt)

### Problem

After the initial implementation was working, a SOLID analysis identified
four violations across the five Java classes:

| Principle | Violation |
|---|---|
| **S** — Single Responsibility | `FlowAspect` owned JSON serialisation + error formatting + AOP interception |
| **O** — Open/Closed | Adding a second transport (e.g. WebSocket) required editing `FlowAspect`, `FlowExecutorService`, and `FlowController` |
| **I** — Interface Segregation | `FlowSseRegistry` exposed all methods to all consumers even though no consumer needed all of them |
| **D** — Dependency Inversion | `FlowAspect`, `FlowController`, and `FlowExecutorService` all injected the concrete `FlowSseRegistry` class |

### Fix

Four targeted changes:

**1. Extract `TraceSerializer` (SRP)**
All JSON serialization and error-message formatting moved out of `FlowAspect`
into a dedicated `@Component`. `FlowAspect` calls `serializer.serializeArgs()`,
`serializer.serializeValue()`, and `serializer.buildErrorMessage()`.

**2. Extract `FlowUrlBuilder` (SRP)**
URL construction and path-parameter substitution moved out of `FlowExecutorService`
into a dedicated `@Component`. `FlowExecutorService` calls `urlBuilder.build(request)`.

**3. Introduce `spi/` interfaces (ISP + DIP)**

```java
// Consumed by FlowAspect + FlowExecutorService
public interface TraceEventSink {
    void pushStep(String traceId, TraceEvent event);
    void pushDone(String traceId, FlowDoneEvent event);
    void pushError(String traceId, String message);
}

// Consumed by FlowController only
public interface TraceEmitterProvider {
    void register(String traceId);
    SseEmitter attach(String traceId);
}
```

`FlowSseRegistry` implements both. Each consumer sees only the methods it needs.

**4. Inject `ObjectMapper` and `RestClient` as `@Bean` (DIP)**
Both were previously instantiated inline with `new ObjectMapper()` and
`RestClient.create()`. `AgenticDocsFlowAutoConfiguration` now declares them as
`@Bean @ConditionalOnMissingBean`, making them overridable.

### Files Changed

- **New:** `spi/TraceEventSink.java`, `spi/TraceEmitterProvider.java`
- **New:** `serializer/TraceSerializer.java`, `url/FlowUrlBuilder.java`
- **Modified:** `registry/FlowSseRegistry.java` — `implements TraceEventSink, TraceEmitterProvider`; `ObjectMapper` injected
- **Modified:** `aspect/FlowAspect.java` — injects `TraceEventSink` + `TraceSerializer`
- **Modified:** `controller/FlowController.java` — injects `TraceEmitterProvider`
- **Modified:** `executor/FlowExecutorService.java` — injects `TraceEventSink`, `FlowUrlBuilder`, `RestClient`
- **Modified:** `autoconfigure/AgenticDocsFlowAutoConfiguration.java` — `@Bean ObjectMapper`, `@Bean RestClient`

---

## Summary Table

| # | Bug / Refactor | Where | Impact | Status |
|---|---|---|---|---|
| 1 | AOP pointcut excludes sample app | `FlowAspect.java` | No steps visible | ✅ Fixed |
| 2 | Step cards in wrong order | `FlowTracer.jsx` | Confusing call chain | ✅ Fixed |
| 3 | `@Operation` import not on classpath | `OrderFlowController.java` | Build failure | ✅ Fixed |
| 4 | Bare `&` in Javadoc comments | Multiple controllers | Build failure | ✅ Fixed |
| 5 | `agentic-docs-flow` not in local `.m2` | Root `pom.xml` / build order | Build failure | ✅ Fixed |
| 6 | SOLID violations (S, O, I, D) | `FlowAspect`, `FlowSseRegistry`, `FlowExecutorService`, `FlowController` | Design debt | ✅ Refactored |
