# 08 — Flow Tracer: Configuration Reference

## Property Summary

| Property | Default | Description |
|---|---|---|
| `agentic.docs.flow.enabled` | `false` | Master switch — must be `true` to activate Flow Tracer |
| `server.port` | `8080` | Used by `FlowExecutorService` to call the target endpoint |

---

## Enabling Flow Tracer

Add to your application's `application.properties`:

```properties
agentic.docs.flow.enabled=true
```

Or in `application.yml`:

```yaml
agentic:
  docs:
    flow:
      enabled: true
```

When this property is `false` (or absent), **no Spring beans are registered**:
- No `FlowAspect` proxy wrapping
- No `FlowSseRegistry` in memory
- No `/agentic-docs/api/flow/*` endpoints
- Zero overhead to normal request processing

---

## Server Port Detection

`FlowUrlBuilder` builds the target URL as `http://localhost:{server.port}`. It reads the port from Spring's environment:

```java
@Value("${server.port:8080}")
private int serverPort;
```

If your application runs on a non-standard port (e.g. 9090), set:

```properties
server.port=9090
```

Flow Tracer will automatically pick it up — no separate configuration needed.

---

## Auto-Configuration

Flow Tracer is registered as a Spring Boot auto-configuration:

```
src/main/resources/META-INF/spring/
  org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

```
com.agentic.docs.core.autoconfigure.AgenticDocsAutoConfiguration
com.agentic.docs.flow.autoconfigure.AgenticDocsFlowAutoConfiguration
```

`AgenticDocsFlowAutoConfiguration` also declares two infrastructure beans with
`@ConditionalOnMissingBean` so they don't conflict with the host application:

```java
@Bean
@ConditionalOnMissingBean
public ObjectMapper flowObjectMapper() { return new ObjectMapper(); }

@Bean
@ConditionalOnMissingBean
public RestClient flowRestClient() { return RestClient.create(); }
```

If the host application already exposes an `ObjectMapper` or `RestClient` bean,
those will be used instead.

---

## AOP Pointcut Scope

By default, `FlowAspect` intercepts all `@Service`, `@RestController`, and
`@Repository` beans **except**:

- `com.agentic.docs.flow..*` (the tracer internals)
- `com.agentic.docs.core..*` (the framework core)

All other packages — including your application's packages — are in scope.

There is currently no configuration property to adjust the pointcut scope at
runtime. If you need to exclude specific packages from tracing, modify the
`@Around` expression in `FlowAspect.java` directly.

---

## SSE Timeout

The SSE connection stays open for 120 seconds:

```java
new SseEmitter(120_000L)
```

This is hard-coded. For very slow endpoints (longer than 120 seconds), the
SSE connection will close before the `done` event arrives, and the browser
will show "SSE connection lost". A future improvement would expose this as
a configurable property:

```properties
# Proposed — not yet implemented
agentic.docs.flow.sse-timeout-ms=120000
```

---

## Production Safety

Flow Tracer is designed to be safe when the property is `false` (default):

✅ **Zero bean overhead** — `@ConditionalOnProperty(matchIfMissing=false)` skips
   the entire `@ComponentScan`

✅ **Zero AOP overhead** — no `@Aspect` bean means no proxy wrapping anywhere

✅ **Zero endpoint exposure** — `/agentic-docs/api/flow/*` does not exist

✅ **Zero memory use** — `FlowSseRegistry`'s `ConcurrentHashMap` is not created

**Recommendation:** Never set `agentic.docs.flow.enabled=true` in a production
`application.properties`. Use a dev-only profile:

```properties
# application-dev.properties (only loaded with --spring.profiles.active=dev)
agentic.docs.flow.enabled=true
```

Or use environment variable override:

```bash
java -jar myapp.jar --agentic.docs.flow.enabled=true
```

---

## Maven Module Inclusion

Flow Tracer is included transitively via `agentic-docs-spring-boot-starter`.
If you add the starter to your `pom.xml`, you automatically get Flow Tracer:

```xml
<dependency>
    <groupId>com.agentic.docs</groupId>
    <artifactId>agentic-docs-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

To include Flow Tracer standalone (without the full Agentic Docs suite):

```xml
<dependency>
    <groupId>com.agentic.docs</groupId>
    <artifactId>agentic-docs-flow</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then register the auto-configuration manually (if not using the starter's
`AutoConfiguration.imports` file), or create your own
`@EnableAgenticDocsFlow` annotation.

---

## Dependency Tree

`agentic-docs-flow` introduces these dependencies into your application:

| Artifact | Scope | Required |
|---|---|---|
| `spring-boot-starter-aop` | compile | Yes — for `@Aspect` and CGLIB proxies |
| `spring-boot-starter-web` | provided | Already on classpath in any web app |
| `spring-boot-autoconfigure` | provided | Already on classpath in any Boot app |

`spring-boot-starter-aop` adds:
- `aspectjweaver` (the AspectJ runtime for expression parsing)
- `spring-aop` (the proxy engine)

Both are lightweight and widely used.
