# 09 — Extending the Starter

## Customising the System Prompt

The system prompt is fully configurable via `application.properties` — no code change required:

```properties
agentic.docs.system-prompt=You are a terse API assistant. Answer in bullet points only. \
  Keep answers under 200 words.\n\nAPI Context:\n---\n{context}\n---
```

**Important:** your custom prompt must include the `{context}` placeholder. The RAG pipeline replaces it with the retrieved endpoint text at runtime.

The built-in `DEFAULT_SYSTEM_PROMPT` in `AgenticDocsChatService` is used when this property is absent or blank. It includes anti-injection guardrails — if you provide a custom prompt, consider keeping equivalent boundaries.

To provide the prompt programmatically (e.g., loaded from a database), register a `@Primary` `ChatPort` bean that overrides the prompt resolution logic:

```java
@Bean
@Primary
public ChatPort myCustomChatPort(LlmPort llmPort, VectorStorePort vectorStorePort,
                                  AgenticDocsProperties properties) {
    return new MyCustomChatService(llmPort, vectorStorePort, properties);
}
```

---

## Custom Chat Implementation

Streaming is built into the starter via `POST /agentic-docs/api/chat/stream` (see [16-ollama-streaming-performance.md](./16-ollama-streaming-performance.md)). The default `AgenticDocsChatService` implements the `ChatPort` interface, which provides both blocking and streaming in a single contract:

```java
public interface ChatPort {
    ChatResponse answer(ChatRequest request);
    Flux<String> streamAnswer(ChatRequest request);
}
```

To provide a **custom** implementation (e.g., caching, A/B routing, multi-model), implement `ChatPort` and register it as the primary bean:

```java
@Bean
@Primary
public ChatPort myChatPort(LlmPort llmPort, VectorStorePort vectorStorePort,
                            AgenticDocsProperties properties) {
    return new MyCachingChatPort(llmPort, vectorStorePort, properties);
}
```

Both the blocking `/chat` and streaming `/chat/stream` endpoints delegate to `chatPort.answer()` and `chatPort.streamAnswer()` respectively — your implementation is picked up automatically.

**Frontend — the UI already uses SSE streaming via `sendChatMessageStream()` in `chatApi.js`:**
```javascript
sendChatMessageStream(question, onToken, onDone, onError)
// onToken is called for each token string as it arrives
// returns an abort() function
```

---

## Adding Conversation History (Multi-Turn)

To support follow-up questions, send the conversation history with each request:

**Backend — update `ChatRequest`:**
```java
public record ChatRequest(
    String question,
    List<MessageDto> history  // previous messages
) {}

public record MessageDto(String role, String content) {}
```

**Backend — build the prompt with history:**
```java
var promptSpec = chatClient.prompt()
        .system(s -> s.text(SYSTEM_PROMPT).param("context", context));

// Add history messages
for (MessageDto msg : request.history()) {
    if ("user".equals(msg.role())) {
        promptSpec = promptSpec.user(msg.content());
    } else {
        promptSpec = promptSpec.assistant(msg.content());
    }
}

String answer = promptSpec.user(request.question()).call().content();
```

**Frontend — send history with each request:**
```javascript
body: JSON.stringify({
    question,
    history: messages.slice(1) // exclude the initial greeting
        .map(m => ({ role: m.role, content: m.content }))
})
```

---

## Adding Authentication

To protect the chat endpoint with Spring Security:

```xml
<!-- Add to sample-app or host app pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/agentic-docs/**").authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .build();
    }
}
```

---

## Adding a Custom Endpoint Filter

To exclude certain endpoints from being indexed (e.g., internal health checks):

```java
// Override ApiMetadataScanner in your host app
@Component
@Primary
public class FilteredApiMetadataScanner extends ApiMetadataScanner {

    public FilteredApiMetadataScanner(RequestMappingHandlerMapping mapping) {
        super(mapping);
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        super.onApplicationEvent(event);
        // Filter out actuator and internal endpoints
        this.scannedEndpoints = this.scannedEndpoints.stream()
                .filter(e -> !e.path().startsWith("/actuator"))
                .filter(e -> !e.path().startsWith("/internal"))
                .toList();
    }
}
```

---

## Switching to a Persistent Vector Store

See [05-configuration-reference.md](./05-configuration-reference.md) for the full property reference.

The key change is in the starter's `pom.xml` — remove `spring-ai-simple-vector-store` and add the desired store:

```xml
<!-- Remove -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-simple-vector-store</artifactId>
</dependency>

<!-- Add (example: pgvector) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
</dependency>
```

Also remove the `VectorStoreConfig` bean — the pgvector starter provides its own `VectorStore` bean via autoconfiguration.

---

## Contributing

### Project conventions

- Java 21 features are encouraged: records, text blocks, pattern matching, sealed classes
- No Lombok — the codebase is intentionally explicit
- All new components in `com.agentic.docs.core` are auto-discovered by `@ComponentScan`
- New configuration properties should go in a `@ConfigurationProperties` class, not hardcoded strings

### Adding a new LLM provider to the starter

1. Add the Spring AI provider starter to `agentic-docs-spring-boot-starter/pom.xml`
2. Add a `@ConditionalOnClass` guard in `AgenticDocsAutoConfiguration` if needed
3. Document the required properties in `05-configuration-reference.md`

### Running tests

```bash
mvn test
```

The sample app has no tests currently. Unit tests for `ApiMetadataScanner` and `AgenticDocsChatController` are the highest-value additions.

### Building the UI after changes

```bash
cd agentic-docs-ui
npm run build
```

The output is committed to `agentic-docs-spring-boot-starter/src/main/resources/static/agentic-docs/` so that the Maven build does not require Node.js.

---

## Roadmap

| Feature | Priority | Complexity |
|---|---|---|
| Streaming responses | High | Medium |
| Configurable system prompt | High | Low |
| Multi-turn conversation | Medium | Medium |
| Authentication support | Medium | Low |
| Persistent vector store option | Medium | Low |
| Code syntax highlighting (highlight.js) | Low | Low |
| Export conversation as Markdown | Low | Low |
| Support for `@Controller` (non-REST) | Low | Medium |
