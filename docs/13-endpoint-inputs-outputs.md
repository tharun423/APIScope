# 13 — Endpoint Inputs & Outputs Panel

## Overview

When you click any endpoint in the **API Explorer** tab, the expanded panel now shows a rich **Inputs & Outputs** section that reveals the full HTTP contract of that endpoint — without reading any code.

This feature was added to address the most common complaint about Swagger: it shows you the path and HTTP method, but to understand what to send and what you get back, you still have to dig through schema definitions manually.

---

## What It Shows

| Row | Color | Source annotation | Example |
|---|---|---|---|
| **Path Params** | 🟡 Amber | `@PathVariable` | `{id}`, `{accountId}` |
| **Query Params** | 🔵 Sky | `@RequestParam` | `?page`, `?size`, `?status` |
| **Request Body** | 🟢 Emerald | `@RequestBody` | `CreateUserRequest`, `TerminationRequest` |
| **Response** | 🟣 Violet | Method return type | `UserResponse`, `List`, `void` |

The panel is only rendered when at least one of these fields has a non-empty value — endpoints like `GET /health` with no parameters and a `void` return type show nothing, keeping the UI clean.

---

## How It Works

### Backend — `ApiMetadataScanner`

At startup, `ApiMetadataScanner` iterates the handler methods registered in `RequestMappingHandlerMapping`. For each method, four new helper methods extract the contract information:

#### Path Parameters

```java
private List<String> extractPathParams(HandlerMethod handlerMethod) {
    List<String> params = new ArrayList<>();
    for (MethodParameter mp : handlerMethod.getMethodParameters()) {
        PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
        if (pv != null) {
            String name = (pv.value() != null && !pv.value().isBlank())
                ? pv.value()
                : mp.getParameterName();
            params.add(name != null ? name : "param" + mp.getParameterIndex());
        }
    }
    return Collections.unmodifiableList(params);
}
```

It reads the `value` attribute of `@PathVariable` first (explicit name), then falls back to the Java parameter name compiled into the bytecode. Spring Boot enables the `-parameters` compiler flag by default so parameter names are always available.

#### Query Parameters

Same pattern — reads `@RequestParam` annotations. Only annotated parameters are included; plain `HttpServletRequest` or `Model` parameters are ignored.

#### Request Body Type

```java
private String extractRequestBodyType(HandlerMethod handlerMethod) {
    for (MethodParameter mp : handlerMethod.getMethodParameters()) {
        if (mp.hasParameterAnnotation(RequestBody.class)) {
            return mp.getParameterType().getSimpleName();
        }
    }
    return null;
}
```

Returns the simple class name (e.g. `CreateUserRequest`) rather than the fully qualified name. This is intentional — the UI is for human reading, not code generation. The simple name is sufficient to identify the DTO.

#### Response Type

```java
private String extractResponseType(HandlerMethod handlerMethod) {
    Class<?> returnType = handlerMethod.getMethod().getReturnType();
    if (returnType == void.class || returnType == Void.class) return "void";
    if ("ResponseEntity".equals(returnType.getSimpleName())) {
        Type genericReturn = handlerMethod.getMethod().getGenericReturnType();
        if (genericReturn instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0) {
                String typeName = args[0].getTypeName();
                int lastDot = typeName.lastIndexOf('.');
                return typeName.substring(lastDot + 1).replace(">", "");
            }
        }
    }
    return returnType.getSimpleName();
}
```

This unwraps `ResponseEntity<T>` to show `T` directly. Most Spring controllers return `ResponseEntity<SomeDto>` — showing `ResponseEntity` in the UI would be meaningless to the developer. The generic type argument is extracted via `ParameterizedType` and stripped of package prefix and closing angle bracket.

### Data Flow

```
Spring context startup
        │
        ▼
ApiMetadataScanner.onApplicationEvent()
        │  iterates RequestMappingHandlerMapping
        │  calls extractPathParams(), extractQueryParams(),
        │         extractRequestBodyType(), extractResponseType()
        ▼
ApiEndpointMetadata record (9 fields)
        │
        ├── toLlmReadableText()  ──►  VectorStore (for AI context)
        │
        └── JSON via GET /agentic-docs/api/endpoints  ──►  React UI
```

### Backend — `ApiEndpointMetadata` Record

The record was extended with four new fields:

```java
public record ApiEndpointMetadata(
        String path,
        String httpMethod,
        String controllerName,
        String methodName,
        String description,
        List<String> pathParams,      // NEW
        List<String> queryParams,     // NEW
        String requestBodyType,       // NEW
        String responseType           // NEW
)
```

`toLlmReadableText()` was also updated to include all four fields, so the AI chat benefits from the same information:

```
Endpoint      : [POST] /api/v1/subscriptions/{id}/terminate
Controller    : PaymentsController
Method        : terminateSubscription
Path Params   : id
Query Params  : none
Request Body  : TerminationRequest
Response Type : SubscriptionResponse
Summary       : Terminate a subscription with refund type
```

This richer LLM context means the AI can now accurately answer questions like:
- *"What do I need to send to terminate a subscription?"*
- *"What does the terminate endpoint return?"*

### Frontend — `EndpointRow.jsx`

The `EndpointRow` component renders the panel conditionally:

```jsx
{(
  (endpoint.pathParams?.length > 0) ||
  (endpoint.queryParams?.length > 0) ||
  endpoint.requestBodyType ||
  endpoint.responseType
) && (
  <div className="mt-3 rounded-xl border border-white/8 overflow-hidden">

    {/* Path Params — amber badges */}
    {endpoint.pathParams?.length > 0 && (
      <div className="...">
        {endpoint.pathParams.map((p) => (
          <span key={p} className="... text-amber-300 ...">{`{${p}}`}</span>
        ))}
      </div>
    )}

    {/* Query Params — sky badges */}
    {endpoint.queryParams?.length > 0 && (
      <div className="...">
        {endpoint.queryParams.map((p) => (
          <span key={p} className="... text-sky-300 ...">?{p}</span>
        ))}
      </div>
    )}

    {/* Request Body — emerald badge */}
    {endpoint.requestBodyType && (
      <span className="... text-emerald-300 ...">{endpoint.requestBodyType}</span>
    )}

    {/* Response — violet badge */}
    {endpoint.responseType && (
      <span className="... text-violet-300 ...">{endpoint.responseType}</span>
    )}

  </div>
)}
```

---

## Color Design Rationale

The four colors are not arbitrary — they map to four distinct roles in an HTTP contract:

| Color | Meaning | Why this color |
|---|---|---|
| 🟡 Amber / Yellow | Path variable — part of the URL itself | "Warning" — if you get this wrong the URL won't resolve |
| 🔵 Sky / Blue | Query string — optional, filtered | Cool/calm — these are optional and low-stakes |
| 🟢 Emerald / Green | Request body — the structured payload | Green = "go" — this is what you send to make something happen |
| 🟣 Violet | Response — what comes back | Violet = the Agentic Docs brand color — what the system returns to you |

---

## Comparison with Swagger

| Capability | Swagger (collapsed) | Swagger (expanded) | Agentic Docs |
|---|---|---|---|
| See HTTP method | ✅ | ✅ | ✅ |
| See path | ✅ | ✅ | ✅ |
| See path params | ❌ | ✅ (requires click + scroll) | ✅ (on first expand) |
| See query params | ❌ | ✅ (requires click + scroll) | ✅ (on first expand) |
| See request body type | ❌ | ✅ (schema panel) | ✅ (on first expand) |
| See response type | ❌ | ✅ (schema panel) | ✅ (on first expand) |
| Ask a question about it | ❌ | ❌ | ✅ (Ask AI button) |
| Execute it | ❌ | ✅ | ✅ (Try it out panel) |

---

## Rebuilding After Changes

If you modify the React component, rebuild the UI and restart the app:

```bash
# 1. Rebuild UI
cd agentic-docs-ui
npm run build

# 2. Rebuild Maven modules
cd ..
mvn install -DskipTests

# 3. Restart the sample app
cd agentic-docs-sample-app
mvn spring-boot:run
```

The `npm run build` step outputs directly to `agentic-docs-spring-boot-starter/src/main/resources/static/agentic-docs/` — the same path that gets packaged into the starter JAR.

---

## Extending This Feature

### Adding More Fields

To add a new field (e.g. `produces` content type from `@RequestMapping(produces = ...)`):

1. Add the field to `ApiEndpointMetadata` record
2. Add an extraction helper in `ApiMetadataScanner`
3. Call the helper when building the record
4. Render the new field in `EndpointRow.jsx`
5. Update `toLlmReadableText()` if the LLM should also see it
6. Update test constructors in `ApiEndpointMetadataTest` and `ApiDocumentIngestorTest`

### Showing Full DTO Schema

Currently only the DTO class name is shown (e.g. `CreateUserRequest`). To show individual fields:

- Use `mp.getParameterType().getDeclaredFields()` to iterate fields
- Extract `@NotNull`, `@Size`, `@Pattern` constraint annotations
- Return a `List<FieldMetadata>` record instead of a `String`
- Render as a collapsible field table in `EndpointRow.jsx`

This is the next logical enhancement — see [`docs/09-extending-the-starter.md`](./09-extending-the-starter.md) for the general extension pattern.
