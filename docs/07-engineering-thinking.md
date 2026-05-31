# 07 — Engineering Thinking

This document captures the complete reasoning behind every significant decision made during the design and implementation of Agentic Docs. It is written for contributors, reviewers, and anyone who wants to understand not just *what* was built but *why*.

---

## The Starting Point — Reading the Problem

The README described a "Spring Boot Starter that turns static API documentation into an interactive AI Agent." Three words in that sentence drove every subsequent decision:

1. **Starter** — must be plug-and-play, zero-config, non-invasive
2. **Static** → **Interactive** — the transformation is the product
3. **AI Agent** — not just a search box; must reason and generate code

The use case (Payments & Ledger microservice, 150+ endpoints, new developer onboarding) made the target user concrete: a developer who is competent but unfamiliar with this specific codebase.

---

## Decision 1 — Multi-Module Maven vs. Single Module

**Options considered:**
- Single Spring Boot application with everything in one module
- Multi-module Maven with clean separation

**Decision: Multi-module**

**Reasoning:**  
The starter pattern requires a library module (`core`) that can be embedded in *any* Spring Boot app. If everything was in one module, the host app would inherit all of Agentic Docs' dependencies — including the embedded Tomcat, which would conflict with the host's own server.

The multi-module split enforces the correct dependency scopes:
- `core` has `spring-boot-starter-web` as `provided` — it compiles against Spring MVC but does not bring Tomcat
- `starter` brings the AI dependencies that the host app needs
- `sample-app` is the only runnable artifact

This is the same pattern used by Spring Security, Spring Data, and every other Spring ecosystem library.

---

## Decision 2 — How to Discover Endpoints

**Options considered:**

**A. Reflection over all beans**  
Iterate `ApplicationContext.getBeansWithAnnotation(RestController.class)`, then use reflection to find methods annotated with `@GetMapping`, `@PostMapping`, etc.

**B. `RequestMappingHandlerMapping.getHandlerMethods()`**  
Use the Spring MVC infrastructure that has already processed all mappings.

**C. Springdoc's OpenAPI model**  
Parse the OpenAPI JSON that springdoc generates.

**Decision: Option B**

**Reasoning:**  
Option A is fragile. It requires manually handling `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`, and their `value`/`path` attributes, plus class-level `@RequestMapping` prefixes. Spring MVC already does all of this correctly — using `RequestMappingHandlerMapping` means we get the authoritative, fully-resolved mapping for free.

Option C creates a hard dependency on springdoc. Not all Spring Boot apps use springdoc. The starter must work without it.

Option B is the correct Spring-idiomatic approach. It is the same mechanism used by Spring Boot Actuator's `/actuator/mappings` endpoint.

---

## Decision 3 — When to Scan (Event Timing)

**Options considered:**

**A. `@PostConstruct` on the scanner bean**  
Run scanning when the scanner bean is initialized.

**B. `ApplicationListener<ContextRefreshedEvent>`**  
Run scanning after the full application context is ready.

**C. `ApplicationRunner` / `CommandLineRunner`**  
Run scanning after the application is fully started.

**Decision: Option B**

**Reasoning:**  
`@PostConstruct` runs during bean initialization, before all beans are fully wired. `RequestMappingHandlerMapping` may not have processed all mappings yet at that point.

`ApplicationRunner` runs after the context is started, which is correct, but it runs after the application is ready to serve requests. If a request comes in before the runner completes, the vector store would be empty.

`ContextRefreshedEvent` fires after the context is fully initialized but before the application starts serving requests. This is the correct window — all beans are ready, all mappings are registered, but no requests have been served yet.

The `@Order(1)` on `ApiDocumentIngestor` ensures it runs after `ApiMetadataScanner` within the same event cycle.

---

## Decision 4 — SimpleVectorStore vs. External Vector DB

**Options considered:**
- `SimpleVectorStore` (in-memory, Spring AI built-in)
- pgvector (PostgreSQL extension)
- Pinecone (managed cloud service)
- Weaviate / Qdrant (self-hosted)

**Decision: `SimpleVectorStore` as default**

**Reasoning:**  
The core constraint is "zero infrastructure." A developer adding one Maven dependency should not need to spin up a PostgreSQL instance or create a Pinecone account to try the starter.

`SimpleVectorStore` stores embeddings as `float[]` arrays in a `HashMap`. For a typical API with 50–500 endpoints, this is:
- ~1,500 floats per document (text-embedding-3-small dimension)
- ~500 documents maximum
- ~3MB of heap memory total

This is negligible. The O(n) cosine similarity search over 500 documents takes microseconds.

The `VectorStore` interface is the abstraction. Any team that needs persistence can swap `SimpleVectorStore` for pgvector by changing one dependency and adding connection properties. The rest of the code is unchanged.

---

## Decision 5 — The System Prompt Design

The system prompt is the most important piece of the entire system. A poorly designed prompt produces hallucinated endpoints, verbose answers, or answers that ignore the retrieved context.

**First draft (rejected):**
```
You are a helpful assistant. Answer questions about the API.
```
Too vague. The model will use its training data to invent plausible endpoints.

**Second draft (rejected):**
```
Answer only using the provided context. Context: {context}
```
No persona, no output format guidance, no fallback instruction.

**Final design:**
```
You are an expert API assistant embedded inside developer documentation.
Your sole job is to help developers understand and use the REST APIs of THIS application.

Rules:
- Answer ONLY using the API context provided below. Do not invent endpoints.
- When asked for implementation, generate concise, correct Java or React code snippets
  using the exact paths, HTTP methods, and field names from the context.
- If the answer cannot be derived from the context, say:
  "I could not find a relevant endpoint for that. Please check the Swagger UI."
- Keep answers focused and developer-friendly.

API Context:
---
{context}
---
```

**Why each element:**

- **Persona** ("expert API assistant") — sets the model's role and tone
- **"THIS application"** — grounds the model in the specific context, not general API knowledge
- **"Do not invent endpoints"** — explicit prohibition is more effective than implicit grounding
- **Code generation instruction** — tells the model to use exact paths and field names from context, not invented ones
- **Fallback instruction** — prevents confident wrong answers; the model knows what to say when it doesn't know
- **"focused and developer-friendly"** — prevents verbose, essay-style answers

---

## Decision 6 — topK=5 for Similarity Search

**Options considered:** topK=3, topK=5, topK=10

**Decision: topK=5**

**Reasoning:**  
- topK=3 may miss relevant endpoints for complex multi-step questions (e.g., "cancel subscription AND calculate refund" needs at least 2 endpoints)
- topK=10 adds ~1,000 tokens of context per request, increasing cost and potentially diluting the relevant signal with noise
- topK=5 is the standard recommendation in RAG literature for small-to-medium document sets

For a 150-endpoint API, 5 documents is ~500 tokens of context — well within the context window of any modern model and sufficient for most developer questions.

---

## Decision 7 — `@ConditionalOnProperty` as the Activation Gate

**Options considered:**

**A. Always active when on classpath**  
No property needed — just add the dependency.

**B. `@ConditionalOnProperty(agentic.docs.enabled=true)`**  
Explicit opt-in required.

**Decision: Option B**

**Reasoning:**  
Option A would activate the agent in production environments where it was added as a transitive dependency or accidentally left in. The agent makes outbound API calls to OpenAI — this has cost and latency implications.

Option B requires a deliberate decision. Teams can enable it in `application-dev.properties` and leave it disabled in `application-prod.properties`. This is the same pattern used by Spring Boot DevTools (`spring.devtools.restart.enabled`).

---

## Decision 8 — `@ComponentScan` vs. Explicit `@Bean` Methods in AutoConfiguration

**Options considered:**

**A. `@ComponentScan(basePackages = "com.agentic.docs.core")`**  
Discovers all `@Component`, `@Configuration`, `@RestController` beans automatically.

**B. Explicit `@Bean` methods in `AgenticDocsAutoConfiguration`**  
```java
@Bean public ApiMetadataScanner scanner(...) { ... }
@Bean public VectorStore vectorStore(...) { ... }
// etc.
```

**Decision: Option A**

**Reasoning:**  
Option B creates tight coupling between the autoconfiguration and every class in core. Adding a new component to core would require updating the autoconfiguration. This violates the Open/Closed Principle.

Option A is self-extending. New components added to `com.agentic.docs.core` are automatically discovered. The autoconfiguration only needs to know the package name.

The trade-off is that `@ComponentScan` is slightly less explicit. This is acceptable because the package name (`com.agentic.docs.core`) is a stable contract.

---

## Decision 9 — React 18 + Tailwind v4 + No State Library

**Options considered for styling:**
- Tailwind CSS v4 (utility-first)
- CSS Modules
- Styled Components
- Plain CSS

**Decision: Tailwind CSS v4**

**Reasoning:**  
Tailwind v4 with the `@tailwindcss/vite` plugin requires zero configuration — no `tailwind.config.js`, no PostCSS config. Just `@import "tailwindcss"` in the CSS file. This aligns with the zero-config philosophy of the project.

**Options considered for state:**
- Redux Toolkit
- Zustand
- React Context
- Local `useState`

**Decision: Local `useState`**

**Reasoning:**  
The UI has exactly two pieces of state: the message list and the loading flag. Both live in the root `App` component and are passed down as props. There is no cross-component state sharing that would justify a state library. Adding Redux or Zustand would be over-engineering.

---

## Decision 10 — Reflective `@Operation` Reading

The `swagger-annotations` dependency is marked `optional` in the core POM. This means:
- If the host app has springdoc on the classpath, `@Operation` summaries are read
- If not, the scanner falls back gracefully to "No description provided."

The alternative was to make `swagger-annotations` a required dependency. This was rejected because it would force every host app to include springdoc even if they use a different documentation tool (e.g., Redoc, custom docs).

The reflective approach (`Class.forName("io.swagger.v3.oas.annotations.Operation")`) is slightly slower than direct annotation access but runs only once at startup. The performance impact is negligible.

---

## What Was Not Built (and Why)

### Streaming responses
The current implementation uses `chatClient.call().content()` which waits for the full response. Streaming (`chatClient.stream()`) would improve perceived latency for long answers. It was not implemented because it requires Server-Sent Events on the backend and a streaming reader on the frontend — significant complexity for a v1.

### Conversation history / multi-turn
The current API is stateless — each request is independent. Multi-turn would require session management (storing message history server-side or sending it with each request). This is a natural v2 feature.

### Authentication
The `/agentic-docs/api/chat` endpoint has no authentication. For internal developer tools this is acceptable. Production deployments should add Spring Security to protect the endpoint.

### Persistent vector store by default
`SimpleVectorStore` loses its data on restart. This means re-embedding on every startup. For a 150-endpoint API with `text-embedding-3-small`, this costs approximately $0.000015 per startup — negligible. Persistence was not added to keep the zero-infrastructure promise.
