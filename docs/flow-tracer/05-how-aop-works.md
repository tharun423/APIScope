# 05 — How AOP Works in Flow Tracer

## What Is Spring AOP?

Spring AOP (Aspect-Oriented Programming) is a mechanism that lets you inject
cross-cutting behaviour (logging, metrics, tracing) into existing beans *without
modifying their source code*.

Flow Tracer uses Spring AOP to intercept every method call on `@Service`,
`@RestController`, and `@Repository` beans — automatically, with no annotations
required in the host application.

---

## Proxy-Based, Not Bytecode

Spring AOP uses **dynamic proxies**, not bytecode manipulation (unlike AspectJ
weaving). This means:

- Spring wraps every matching bean in a JDK dynamic proxy (for interface-based beans)
  or a CGLIB proxy (for class-based beans)
- The proxy intercepts calls and delegates to the `@Around` advice *before and after*
  forwarding to the real object
- No Java agent is needed; no compile-time processing; no change to classpath

**Trade-off:** Proxy-based AOP only intercepts calls *from outside the bean*.
If `ServiceA.methodA()` calls `this.methodB()` internally, `methodB()` is NOT
intercepted — the call bypasses the proxy. This is a fundamental Spring AOP
limitation.

---

## The Pointcut Expression

```java
@Around("""
    (within(@org.springframework.stereotype.Service *)
     || within(@org.springframework.web.bind.annotation.RestController *)
     || within(@org.springframework.stereotype.Repository *))
    && !within(com.agentic.docs.flow..*)
    && !within(com.agentic.docs.core..*)
    """)
public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable { ... }
```

### Breaking It Down

| Clause | Meaning |
|---|---|
| `within(@Service *)` | Any method in any class annotated with `@Service` |
| `within(@RestController *)` | Any method in any class annotated with `@RestController` |
| `within(@Repository *)` | Any method in any class annotated with `@Repository` |
| `!within(com.agentic.docs.flow..*)` | Exclude the Flow Tracer internals (prevents infinite loops) |
| `!within(com.agentic.docs.core..*)` | Exclude the core framework beans (metrics, chat, vector store) |

### Why Not Exclude `com.agentic.docs.sample.*`?

The sample app lives in `com.agentic.docs.sample`. If you write:

```java
&& !within(com.agentic.docs..*)   // ← WRONG — excludes everything under com.agentic.docs
```

...you accidentally exclude `com.agentic.docs.sample.*` too, and no steps appear.

The correct exclusion is targeted:
- `!within(com.agentic.docs.flow..*)` — our own tracer
- `!within(com.agentic.docs.core..*)` — the framework core

The sample app (`com.agentic.docs.sample.*`) is intentionally NOT excluded
because that is the code we want to trace.

---

## Step Index Assignment

```java
int stepIndex = registry.nextStepIndex(traceId);  // ← BEFORE proceed()
// ...
Object result = pjp.proceed();                      // ← real method executes
// ...
registry.pushStep(traceId, new TraceEvent(..., stepIndex, ...));  // ← AFTER proceed()
```

This ordering is deliberate:

```
Thread execution timeline:

  FlowAspect enters OrderFlowController.checkout()
    stepIndex = 0 assigned
    proceed() called → enters real method
      FlowAspect enters OrderService.validateOrder()
        stepIndex = 1 assigned
        proceed() called → method completes
        pushStep(stepIndex=1) ← SSE fires here (inner method done first)
      FlowAspect enters InventoryService.checkAvailability()
        stepIndex = 2 assigned
        proceed() called → method completes
        pushStep(stepIndex=2) ← SSE fires here
      ...
    real method completes
    pushStep(stepIndex=0) ← SSE fires here (outer method done last)
```

SSE events arrive in **completion order** (1, 2, 3, ... then 0).
The UI sorts by `stepIndex` to restore **call order** (0, 1, 2, 3, ...).

---

## Header Propagation

Flow Tracer uses an HTTP header `X-Flow-Trace-Id` to tie a Spring request to
its active trace. Spring's `RequestContextHolder` makes the current servlet
request available on the executing thread.

```java
private String extractTraceId() {
    try {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return attrs.getRequest().getHeader("X-Flow-Trace-Id");
    } catch (Exception e) {
        return null;
    }
}
```

**What happens when the header is absent?**
The advice returns `null` for `traceId` and immediately calls `pjp.proceed()`
with no overhead. Normal application requests (not from Flow Tracer) are
completely unaffected.

---

## Thread Propagation Issue

Spring's `RequestContextHolder` uses a `ThreadLocal` to hold the current request.
When `FlowExecutorService` fires the HTTP call, the *new* virtual thread (that
handles the inbound request on Tomcat) will have its own `RequestContextHolder`
populated by Spring's `DispatcherServlet`.

However, if any service method spawns its own background thread internally
(e.g. `CompletableFuture.supplyAsync(...)`), that child thread will NOT have
the `RequestContextHolder` set — so `extractTraceId()` returns `null` and the
call goes untraced. This is a known limitation of proxy-based AOP in async contexts.

---

## `@EnableAspectJAutoProxy`

The `AgenticDocsFlowAutoConfiguration` class is annotated with:

```java
@EnableAspectJAutoProxy
```

This tells Spring to activate proxy creation for all `@Aspect` beans found in
the `com.agentic.docs.flow` component scan. Without this, the `FlowAspect`
bean is created but never applied to any proxy — the advice never runs.

---

## Performance Impact

When `X-Flow-Trace-Id` is absent (normal requests):
- `extractTraceId()` does one `ThreadLocal.get()` + one `getHeader()` call
- This is nanosecond-level overhead — effectively zero

When `X-Flow-Trace-Id` is present (traced request):
- One `ObjectMapper.writeValueAsString()` per method call for serialising args/return
- One `CopyOnWriteArrayList.add()` per step (or `SseEmitter.send()` if emitter attached)
- Total overhead per step: typically 1–5ms on a modern JVM

Traced requests are **developer-initiated through the UI** and never occur in
production unless `agentic.docs.flow.enabled=true` is set.
