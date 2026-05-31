# 05 — Configuration Reference

## Overview

Agentic Docs supports two LLM providers switchable via Spring profiles:

| Profile | Provider | Cost | Internet |
|---|---|---|---|
| `openai` | OpenAI API (gpt-4o-mini + text-embedding-3-small) | ~$0.01/session | Required |
| `ollama` | Local Ollama (llama3.2 + nomic-embed-text) | Free | Not required |

Switch the active provider by changing `spring.profiles.active` in `application.properties`. See [10-switching-llm-providers.md](./10-switching-llm-providers.md) for the full guide.

---

## File Layout

```
agentic-docs-sample-app/src/main/resources/
├── application.properties          ← Shared settings + active profile
└── application-ollama.properties   ← Ollama config (local/offline)
```

OpenAI settings are provided via environment variable (`SPRING_AI_OPENAI_API_KEY`) and Spring AI's auto-configuration — no separate `application-openai.properties` file is needed.

---

## `application.properties` — Shared Settings

```properties
server.port=8080

# ── Active LLM Profile ────────────────────────────────────────────────────────
# openai → Uses OpenAI API (requires SPRING_AI_OPENAI_API_KEY env var)
# ollama → Uses local Ollama (requires ollama serve running on localhost:11434)
spring.profiles.active=openai

# ── Logging ───────────────────────────────────────────────────────────────────
logging.level.com.agentic.docs=INFO
```

---

## `agentic.docs.*` Properties — Full Reference

All properties are bound via `AgenticDocsProperties` (`@ConfigurationProperties(prefix = "agentic.docs")`). Invalid values cause a startup failure with a clear error message (validated via Bean Validation at startup).

| Property | Type | Default | Description |
|---|---|---|---|
| `agentic.docs.enabled` | `boolean` | `true` | Master on/off switch. `matchIfMissing=true` — omitting it also activates the starter. |
| `agentic.docs.top-k` | `int` (1–50) | `5` | Number of most-relevant endpoint chunks to retrieve per question. |
| `agentic.docs.system-prompt` | `String` | *(built-in)* | Override the LLM system prompt. Must contain `{context}` placeholder. |
| `agentic.docs.vector-store-path` | `String` | `./agentic-docs-vector-store.json` | Path for persisting the vector store JSON file across restarts. |
| `agentic.docs.rate-limit.enabled` | `boolean` | `true` | Enable/disable per-IP rate limiting. |
| `agentic.docs.rate-limit.requests-per-minute` | `int` (1–10,000) | `20` | Maximum requests per IP per minute before HTTP 429 is returned. |
| `agentic.docs.cors.allowed-origins` | `List<String>` | `http://localhost:5173` | Origins allowed to call `/agentic-docs/api/**`. |

### Example — full configuration

```properties
# application.properties
agentic.docs.enabled=true
agentic.docs.top-k=5
agentic.docs.vector-store-path=./agentic-docs-vector-store.json
agentic.docs.rate-limit.enabled=true
agentic.docs.rate-limit.requests-per-minute=20
agentic.docs.cors.allowed-origins=http://localhost:5173,https://yourapp.example.com

# Optional — override system prompt
# agentic.docs.system-prompt=You are a terse API assistant. {context}
```

---

## `application-ollama.properties` — Ollama Provider

Activated by `spring.profiles.active=ollama`.

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.embedding.options.model=nomic-embed-text

# Performance tuning
spring.ai.ollama.chat.options.keep-alive=-1
spring.ai.ollama.chat.options.num-ctx=2048
spring.ai.ollama.chat.options.num-predict=512
spring.ai.ollama.chat.options.temperature=0.1
```

### Ollama Properties

| Property | Type | Default | Description |
|---|---|---|---|
| `spring.ai.ollama.base-url` | `String` | `http://localhost:11434` | Ollama server URL |
| `spring.ai.ollama.chat.options.model` | `String` | — | Chat model name (must be pulled first) |
| `spring.ai.ollama.embedding.options.model` | `String` | — | Embedding model name (must be pulled first) |
| `spring.ai.ollama.chat.options.keep-alive` | `String` | `-1` | How long to keep model in RAM. `-1` = indefinitely. Set to e.g. `10m` if RAM is constrained. |
| `spring.ai.ollama.chat.options.num-ctx` | `int` | `4096` | Context window size in tokens. `2048` is sufficient for RAG API docs. |
| `spring.ai.ollama.chat.options.num-predict` | `int` | unlimited | Max tokens to generate per response. `512` (~350 words) is ample for API answers. |
| `spring.ai.ollama.chat.options.temperature` | `float` | `0.8` | Lower = more deterministic. `0.1` improves factual accuracy. |

### Recommended Ollama Models

| Use Case | Model | RAM Required | Pull Command |
|---|---|---|---|
| Chat (small, fast) ✓ | `llama3.2` | ~2 GB | `ollama pull llama3.2` |
| Chat (better quality) | `llama3.1:8b` | ~5 GB | `ollama pull llama3.1:8b` |
| Chat (best quality) | `llama3.3:70b` | ~40 GB | `ollama pull llama3.3:70b` |
| Embeddings ✓ | `nomic-embed-text` | ~274 MB | `ollama pull nomic-embed-text` |
| Embeddings (alternative) | `mxbai-embed-large` | ~670 MB | `ollama pull mxbai-embed-large` |

---

## OpenAI Configuration

Activated by `spring.profiles.active=openai`. No separate properties file is needed — Spring AI auto-configures from the environment variable.

```bash
# Required — set before starting the app
export SPRING_AI_OPENAI_API_KEY=sk-...        # macOS/Linux
$env:SPRING_AI_OPENAI_API_KEY = "sk-..."      # PowerShell
```

Optional overrides in `application.properties`:
```properties
# Default values shown — omit to use these defaults
spring.ai.openai.chat.options.model=gpt-4o-mini
spring.ai.openai.embedding.options.model=text-embedding-3-small
```

---

## Environment Variables

| Variable | Used by | Description |
|---|---|---|
| `SPRING_AI_OPENAI_API_KEY` | OpenAI profile | Your OpenAI API key |
| `SPRING_PROFILES_ACTIVE` | All | Override active profile without editing files |

### Setting environment variables

**Windows PowerShell:**
```powershell
$env:SPRING_AI_OPENAI_API_KEY = "sk-..."
$env:SPRING_PROFILES_ACTIVE = "openai"
```

**macOS / Linux:**
```bash
export SPRING_AI_OPENAI_API_KEY=sk-...
export SPRING_PROFILES_ACTIVE=openai
```

**Docker Compose:**
```yaml
environment:
  - SPRING_AI_OPENAI_API_KEY=sk-...
  - SPRING_PROFILES_ACTIVE=openai
```
