# 22 — Application Properties Configuration Guide

This guide covers every `application.properties` setting you need when integrating the **APIScope Spring Boot Starter** into a new project.

---


## File Layout

Create two files under `src/main/resources/`:

```
src/main/resources/
├── application.properties           ← Shared settings + active LLM profile
└── application-ollama.properties    ← Ollama-specific LLM config
```

---

## `application.properties` — Full Reference

```properties
# ── Server ────────────────────────────────────────────────────────────────────
server.port=8080

# ── LLM Provider Profile ──────────────────────────────────────────────────────
# Activates application-ollama.properties automatically.
# Change to "openai" if you switch to OpenAI (see doc 10 for that setup).
spring.profiles.active=ollama

# ── APIScope Core ──────────────────────────────────────────────────────────────
# Master on/off switch. Default is true — omit this line to keep it enabled.
# apiscope.enabled=true

# Path where the vector store JSON is persisted across restarts.
# On first startup: all endpoint descriptions are embedded and saved here.
# On subsequent startups: loaded from disk — no re-embedding needed.
apiscope.vector-store-path=./apiscope-vector-store.json

# Number of most-relevant endpoint chunks the RAG pipeline retrieves per question.
# Valid range: 1–50. Default: 5. Increase for broader answers; decrease for speed.
# apiscope.top-k=5

# Override the built-in LLM system prompt (optional).
# MUST contain the {context} placeholder — used to inject retrieved API docs.
# apiscope.system-prompt=You are a concise API assistant. {context}

#mandatory
spring.profiles.active=ollama
apiscope.vector-store-path=./apiscope-vector-store.json
apiscope.rate-limit.enabled=true
apiscope.rate-limit.requests-per-minute=20
apiscope.flow.enabled=true

# ── Rate Limiting ─────────────────────────────────────────────────────────────
# Per-IP token-bucket rate limiting on /apiscope/api/chat.
# Disable for internal/trusted networks.
apiscope.rate-limit.enabled=true
apiscope.rate-limit.requests-per-minute=20

# ── CORS ──────────────────────────────────────────────────────────────────────
# Comma-separated list of origins allowed to call /apiscope/api/*.
# Default: http://localhost:5173 (Vite dev server).
# Add your production domain here.
apiscope.cors.allowed-origins=http://localhost:5173,https://yourapp.example.com

# ── Flow Tracer (opt-in) ──────────────────────────────────────────────────────
# Enables real-time AOP execution tracing + SSE streaming for the Flow Tracer UI.
# Keep false in production; enable for dev/demo only.
apiscope.flow.enabled=false

# ── Logging ───────────────────────────────────────────────────────────────────
logging.level.com.apiscope=INFO

# ── Actuator (optional) ───────────────────────────────────────────────────────
# Expose metrics endpoint so the APIScope UI can show live response-time badges.
# Remove this block if you don't use spring-boot-starter-actuator.
management.endpoints.web.exposure.include=metrics
management.endpoint.metrics.enabled=true
management.server.port=8080
```

---

## `application-ollama.properties` — Ollama LLM Config

Activated automatically when `spring.profiles.active=ollama`.

**Prerequisites — run once before starting your app:**
```bash
ollama pull llama3.2          # chat model  (~2 GB)
ollama pull nomic-embed-text  # embedding model (~274 MB)
ollama serve                  # start the Ollama server
```

```properties
# ── Ollama Server ─────────────────────────────────────────────────────────────
spring.ai.ollama.base-url=http://localhost:11434

# ── Models ────────────────────────────────────────────────────────────────────
# IMPORTANT: Use .options.model — NOT .model (Spring AI 1.0.0 key paths)
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.embedding.options.model=nomic-embed-text

# ── Performance Tuning (optional) ─────────────────────────────────────────────
# Keep model loaded in RAM indefinitely — eliminates 10-30s cold-start per request.
# Set to a duration (e.g. 10m) if RAM is constrained.
spring.ai.ollama.chat.options.keep-alive=-1

# Limit context window. RAG API docs are small; 2048 is plenty and faster.
spring.ai.ollama.chat.options.num-ctx=2048

# Cap generated tokens. 512 tokens (~350 words) is enough for any API answer.
spring.ai.ollama.chat.options.num-predict=512

# Lower temperature = more deterministic/factual answers.
spring.ai.ollama.chat.options.temperature=0.1
```

---

## `apiscope.*` Properties — Quick Reference Table

| Property | Type | Default | Description |
|---|---|---|---|
| `apiscope.enabled` | `boolean` | `true` | Master switch. `false` disables all APIScope beans. |
| `apiscope.top-k` | `int` (1–50) | `5` | RAG chunks retrieved per question. |
| `apiscope.system-prompt` | `String` | *(built-in)* | Custom LLM system prompt. Must contain `{context}`. |
| `apiscope.vector-store-path` | `String` | `./apiscope-vector-store.json` | Disk path for persisted vector store. |
| `apiscope.rate-limit.enabled` | `boolean` | `true` | Enable/disable per-IP rate limiting. |
| `apiscope.rate-limit.requests-per-minute` | `int` (1–10000) | `20` | Max chat requests per IP per minute. |
| `apiscope.cors.allowed-origins` | `List<String>` | `http://localhost:5173` | CORS origins for `/apiscope/api/*`. |
| `apiscope.flow.enabled` | `boolean` | `false` | Enable Flow Tracer AOP + SSE streaming. |

---

## `spring.ai.ollama.*` Properties — Quick Reference Table

| Property | Type | Default | Description |
|---|---|---|---|
| `spring.ai.ollama.base-url` | `String` | `http://localhost:11434` | Ollama server URL. |
| `spring.ai.ollama.chat.options.model` | `String` | — | **Required.** Chat model name (must be pulled). |
| `spring.ai.ollama.embedding.options.model` | `String` | — | **Required.** Embedding model (must be pulled). |
| `spring.ai.ollama.chat.options.keep-alive` | `String` | `-1` | How long to keep model in RAM. `-1` = forever. |
| `spring.ai.ollama.chat.options.num-ctx` | `int` | `4096` | Context window token size. |
| `spring.ai.ollama.chat.options.num-predict` | `int` | unlimited | Max tokens per response. |
| `spring.ai.ollama.chat.options.temperature` | `float` | `0.8` | Lower = more factual. `0.1` recommended for API docs. |

---

## Minimal Setup (New Project)

The absolute minimum to get APIScope running with Ollama:

**`application.properties`**
```properties
spring.profiles.active=ollama
apiscope.vector-store-path=./apiscope-vector-store.json
```

**`application-ollama.properties`**
```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.embedding.options.model=nomic-embed-text
```

Everything else uses sensible defaults and is optional.

---

## Common Mistakes

| Mistake | Symptom | Fix |
|---|---|---|
| Using `spring.ai.ollama.chat.model` (no `.options.`) | `EmbeddingModel` bean not found, app fails to start | Use `spring.ai.ollama.chat.options.model` |
| Using `spring.ai.ollama.embedding.model` (no `.options.`) | Same cascade failure | Use `spring.ai.ollama.embedding.options.model` |
| Missing `spring.profiles.active=ollama` | No Ollama config loaded, beans missing | Add `spring.profiles.active=ollama` to `application.properties` |
| `apiscope.vector-store-path` pointing to a read-only dir | `IOException` on startup | Use a writable path like `./apiscope-vector-store.json` |
| `apiscope.flow.enabled=true` in production | AOP overhead on all requests | Keep `false` in production; enable only for dev/demo |

---

## Database Configuration (if your service uses JPA)

If your service uses Spring Data JPA, add the relevant datasource block. Example for MySQL:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/your_db?useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
```

Example for H2 in-memory (dev/test only):

```properties
spring.datasource.url=jdbc:h2:mem:devdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
```

---

## Related Docs

- [05 — Configuration Reference](05-configuration-reference.md) — full property tables for the library itself
- [06 — Getting Started](06-getting-started.md) — adding the starter to a new project
- [10 — Switching LLM Providers](10-switching-llm-providers.md) — switching to OpenAI
- [11 — Ollama Local Setup](11-ollama-local-setup.md) — installing and running Ollama
- [21 — Integration Troubleshooting](21-integration-troubleshooting.md) — common startup failures
