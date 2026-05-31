# 10 — Switching LLM Providers

This is the single source of truth for switching between **OpenAI** (cloud, paid)
and **Ollama** (local, free/offline). Every step is listed explicitly — nothing is assumed.

---

## Quick Reference

| | OpenAI | Ollama |
|---|---|---|
| **Cost** | ~$0.01/session | Free |
| **Internet required** | Yes | No |
| **Setup time** | 2 minutes | 10–15 minutes (one-time) |
| **Response speed** | Fast (~2s) | Depends on hardware |
| **Answer quality** | Excellent | Good (llama3.2) |
| **Privacy** | Data sent to OpenAI | 100% local |
| **Spring profile** | `openai` | `ollama` |
| **Maven profile flag** | `-P openai` (default) | `-P ollama` |

---

## Switching from OpenAI → Ollama (Go Offline)

### Prerequisites — do these once

**1. Install Ollama**

Download from [https://ollama.com/download](https://ollama.com/download) and install for your OS.

Verify it's running:
```bash
ollama --version
```

**2. Pull the required models** (one-time download, ~2.3 GB total)

```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

Verify both are available:
```bash
ollama list
```

You should see both `llama3.2` and `nomic-embed-text` in the list.

**3. Confirm Ollama is serving**

```bash
curl http://localhost:11434
# Expected: "Ollama is running"
```

---

### Step 1 — Change the active profile

Open `agentic-docs-sample-app/src/main/resources/application.properties` and change:

```properties
# BEFORE
spring.profiles.active=openai

# AFTER
spring.profiles.active=ollama
```

That's the only file you need to edit.

---

### Step 2 — Rebuild with the Ollama Maven profile

```bash
# From the repo root
mvn clean install -P ollama -DskipTests
```

This activates the `ollama` Maven profile in the starter POM, which pulls
`spring-ai-ollama-spring-boot-starter` instead of `spring-ai-openai-spring-boot-starter`.

---

### Step 3 — Run the app

```bash
java -jar agentic-docs-sample-app/target/agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
```

No environment variables needed. No API key. Ollama handles everything locally.

---

### Step 4 — Verify it's using Ollama

Check the startup logs for:
```
INFO  c.a.d.c.ingestor.ApiDocumentIngestor : [AgenticDocs] Ingested 8 endpoint documents into the vector store.
```

Then test the chat endpoint:
```bash
curl -X POST http://localhost:8080/agentic-docs/api/chat \
  -H "Content-Type: application/json" \
  -d "{\"question\": \"What endpoints are available?\"}"
```

The response will come from `llama3.2` running on your machine.

---

## Switching from Ollama → OpenAI (Go to Cloud)

### Prerequisites

Get an OpenAI API key from [https://platform.openai.com/api-keys](https://platform.openai.com/api-keys).

---

### Step 1 — Set your API key as an environment variable

**Windows CMD:**
```cmd
set SPRING_AI_OPENAI_API_KEY=sk-your-key-here
```

**Windows PowerShell:**
```powershell
$env:SPRING_AI_OPENAI_API_KEY = "sk-your-key-here"
```

**macOS / Linux:**
```bash
export SPRING_AI_OPENAI_API_KEY=sk-your-key-here
```

---

### Step 2 — Change the active profile

Open `agentic-docs-sample-app/src/main/resources/application.properties` and change:

```properties
# BEFORE
spring.profiles.active=ollama

# AFTER
spring.profiles.active=openai
```

---

### Step 3 — Rebuild with the OpenAI Maven profile

```bash
# From the repo root
mvn clean install -P openai -DskipTests

# OR just (openai is the default profile)
mvn clean install -DskipTests
```

---

### Step 4 — Run the app

```bash
java -jar agentic-docs-sample-app/target/agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
```

---

## Switching Without Rebuilding (Runtime Override)

If you have already built the JAR with both providers on the classpath (not the default setup),
you can override the profile at runtime without rebuilding:

```bash
# Use Ollama at runtime
java -jar agentic-docs-sample-app.jar --spring.profiles.active=ollama

# Use OpenAI at runtime
java -jar agentic-docs-sample-app.jar --spring.profiles.active=openai
```

Or via environment variable:
```bash
# Windows CMD
set SPRING_PROFILES_ACTIVE=ollama
java -jar agentic-docs-sample-app.jar

# macOS / Linux
SPRING_PROFILES_ACTIVE=ollama java -jar agentic-docs-sample-app.jar
```

---

## What Each File Controls

```
application.properties
  └── spring.profiles.active = openai | ollama
        │
        ├── openai  →  application-openai.properties
        │               spring.ai.openai.api-key
        │               spring.ai.openai.chat.options.model=gpt-4o-mini
        │               spring.ai.openai.embedding.options.model=text-embedding-3-small
        │
        └── ollama  →  application-ollama.properties
                        spring.ai.ollama.base-url=http://localhost:11434
                        spring.ai.ollama.chat.options.model=llama3.2
                        spring.ai.ollama.embedding.options.model=nomic-embed-text

pom.xml (starter + sample-app)
  └── Maven profile flag (-P openai | -P ollama)
        │
        ├── -P openai  →  spring-ai-openai-spring-boot-starter on classpath
        └── -P ollama  →  spring-ai-ollama-spring-boot-starter on classpath
```

> **Important:** The Spring profile (`application.properties`) and the Maven profile
> (`-P` flag) must always match. If they don't, the app will start but the AI calls
> will fail because the wrong provider's JAR is on the classpath.

---

## Troubleshooting

### Ollama issues

| Symptom | Cause | Fix |
|---|---|---|
| `Connection refused` on port 11434 | Ollama not running | Run `ollama serve` |
| `model not found: llama3.2` | Model not pulled | Run `ollama pull llama3.2` |
| `model not found: nomic-embed-text` | Embedding model not pulled | Run `ollama pull nomic-embed-text` |
| Very slow responses | Model too large for your hardware | Switch to a smaller model, e.g. `llama3.2:1b` |
| Out of memory | Not enough RAM | Use `llama3.2:1b` (~1 GB) instead of `llama3.2` (~2 GB) |

### OpenAI issues

| Symptom | Cause | Fix |
|---|---|---|
| `401 Unauthorized` | Invalid or missing API key | Check `SPRING_AI_OPENAI_API_KEY` env var |
| `429 Too Many Requests` | Rate limit hit | Wait and retry, or upgrade OpenAI plan |
| `insufficient_quota` | No credits on account | Add credits at platform.openai.com/billing |
| Slow first response | Cold start | Normal — subsequent requests are faster |

### Profile mismatch

| Symptom | Cause | Fix |
|---|---|---|
| `NoSuchBeanDefinitionException: ChatModel` | Maven profile doesn't match Spring profile | Rebuild with correct `-P` flag |
| App starts but chat returns 500 | Wrong provider JAR on classpath | Ensure `-P` flag matches `spring.profiles.active` |

---

## Changing the Ollama Model

To use a different Ollama chat model (e.g., better quality or smaller size):

**1. Pull the new model:**
```bash
ollama pull llama3.1:8b
```

**2. Update `application-ollama.properties`:**
```properties
spring.ai.ollama.chat.options.model=llama3.1:8b
```

**3. Restart the app** (no rebuild needed for model changes).

---

## Using a Different OpenAI Model

Update `application-openai.properties`:

```properties
# Upgrade to GPT-4o for better quality
spring.ai.openai.chat.options.model=gpt-4o

# Or use the latest model
spring.ai.openai.chat.options.model=gpt-4o-2024-11-20
```

No rebuild needed — model names are resolved at runtime by the OpenAI API.
