# 06 — Getting Started

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| Java JDK | 21+ | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node.js | 18+ | `node -v` |
| npm | 9+ | `npm -v` |
| Ollama | Latest | [ollama.com/download](https://ollama.com/download) |

---

## Option A — Run the Sample App (Quickest)

This runs the pre-built demo with the Payments & Subscriptions API.

### Step 1 — Clone the repository

```bash
git clone https://github.com/your-org/agentic-docs.git
cd agentic-docs
```

### Step 2 — Pull Ollama models and start Ollama

```bash
ollama pull llama3.2
ollama pull nomic-embed-text
ollama serve
```

### Step 3 — Build the UI

```bash
cd agentic-docs-ui
npm install
npm run build
cd ..
```

This compiles the React app and places the output in `agentic-docs-spring-boot-starter/src/main/resources/static/agentic-docs/`.

### Step 4 — Build the Maven project

```bash
mvn clean install -DskipTests
```

### Step 5 — Run the sample app

```bash
java -jar agentic-docs-sample-app/target/agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
```

### Step 6 — Open the UI

Navigate to: **http://localhost:8080/agentic-docs/**

You should see the Agentic Docs chat interface. Try asking:
- *"How do I cancel a subscription with a partial refund?"*
- *"What endpoints are available for payments?"*
- *"Generate Java code to process a payment"*

---

## Option B — Add to Your Own Spring Boot App

### Step 1 — Build and install the starter to your local Maven repository

```bash
cd agentic-docs
cd agentic-docs-ui && npm install && npm run build && cd ..
mvn clean install -DskipTests -pl agentic-docs-core,agentic-docs-spring-boot-starter
```

### Step 2 — Add the dependency to your app's `pom.xml`

```xml
<dependency>
    <groupId>com.agentic.docs</groupId>
    <artifactId>agentic-docs-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Step 3 — Add properties to your `application.properties`

```properties
agentic.docs.enabled=true
spring.profiles.active=ollama
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.embedding.options.model=nomic-embed-text
```

### Step 4 — (Optional) Add `@Operation` summaries to your controllers

```java
@Operation(summary = "Cancel a subscription. Body: { refundType: PARTIAL|FULL|NONE }")
@PostMapping("/subscriptions/{id}/cancel")
public ResponseEntity<Void> cancel(@PathVariable String id, @RequestBody CancelRequest req) {
    // ...
}
```

Without `@Operation`, the agent still works — it just uses "No description provided." as the endpoint summary. Adding summaries significantly improves answer quality.

### Step 5 — Run your app and open the UI

```
http://localhost:{your-port}/agentic-docs/
```

---

## Development Mode (UI Hot Reload)

To develop the UI with hot module replacement while the Spring Boot app runs:

**Terminal 1 — Start Spring Boot:**
```bash
java -jar agentic-docs-sample-app/target/agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
```

**Terminal 2 — Start Vite dev server:**
```bash
cd agentic-docs-ui
npm run dev
```

Open: **http://localhost:5173/agentic-docs/**

The Vite dev server proxies `/agentic-docs/api` calls to `localhost:8080`. Changes to React files hot-reload instantly without restarting Spring Boot.

---

## Verifying the Backend is Working

### Check the startup logs

After starting the app, look for these log lines:

```
INFO  c.a.d.c.scanner.ApiMetadataScanner  : [AgenticDocs] Scanned 8 REST endpoints for RAG indexing.
INFO  c.a.d.c.ingestor.ApiDocumentIngestor: [AgenticDocs] Ingested 8 endpoint documents into the vector store.
```

If you see `Scanned 0 REST endpoints`, the scanner is not finding your controllers. Verify:
- Your controllers are annotated with `@RestController` (not just `@Controller`)
- The `agentic.docs.enabled=true` property is set
- The starter is on the classpath

### Test the API directly

```bash
curl -X POST http://localhost:8080/agentic-docs/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "What endpoints are available?"}'
```

Expected response:
```json
{
  "answer": "Based on the indexed endpoints, the following APIs are available: ..."
}
```

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| UI shows 404 | UI not built | Run `npm run build` in `agentic-docs-ui/` |
| `Scanned 0 endpoints` | `agentic.docs.enabled` not set | Add `agentic.docs.enabled=true` to properties |
| `Connection refused` on chat | Spring Boot not running | Start the app first |
| Hallucinated endpoints in answers | No `@Operation` summaries | Add summaries to improve context quality |
| Duplicate scanning logs | Parent/child context | Normal — the guard prevents double-ingestion |
