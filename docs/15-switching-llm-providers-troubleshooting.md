# Switching LLM Providers — Troubleshooting & Execution Guide

This guide documents all errors encountered when switching between **OpenAI** and **Ollama**
providers, explains why they happen, and describes the final working solution.

---

## How Provider Switching Works (Final Architecture)

Both provider JARs (`spring-ai-starter-model-openai` and `spring-ai-starter-model-ollama`)
are **always on the classpath**. The active provider is selected purely at **runtime** using
`spring.profiles.active` in `application.properties`.

Each profile's properties file uses `spring.autoconfigure.exclude` to disable the inactive
provider's Spring Boot auto-configuration so its beans are never created.

```text
application.properties
  └── spring.profiles.active=ollama  ──►  application-ollama.properties
                                              └── spring.autoconfigure.exclude=OpenAi*
                                              └── spring.ai.ollama.*  ──►  OllamaEmbeddingModel ✅
                                                                            OpenAiEmbeddingModel ❌
```

> ✅ **No Maven rebuild is needed when switching providers.**
> Only `spring.profiles.active` in `application.properties` needs to change.

---

## Switching Providers — The Only Step Required

Edit `agentic-docs-sample-app/src/main/resources/application.properties`:

```properties
# Use Ollama (local, free):
spring.profiles.active=ollama

# OR use OpenAI (cloud, paid):
spring.profiles.active=openai
```

Then restart the application. That's it.

---

## Quick Reference

| | Ollama | OpenAI |
| --- | --- | --- |
| `spring.profiles.active` | `ollama` | `openai` |
| Maven rebuild needed? | ❌ No | ❌ No |
| API key required | ❌ No | ✅ `SPRING_AI_OPENAI_API_KEY` env var |
| Cost | Free | ~$0.02 per 1M tokens (embeddings) |
| Internet required | ❌ No (runs locally) | ✅ Yes |
| SSL proxy issues | ❌ Not affected | ✅ Possible in corporate networks |
| Hardware | ~2.5 GB RAM | None (cloud) |
| Chat model | `llama3.2` | `gpt-4o-mini` |
| Embedding model | `nomic-embed-text` | `text-embedding-3-small` |

---

## Ollama Prerequisites (one-time setup)

```powershell
# 1. Install Ollama
#    https://ollama.com/download

# 2. Pull required models (once)
ollama pull llama3.2
ollama pull nomic-embed-text

# 3. Start Ollama before running the app
ollama serve
```

## OpenAI Prerequisites

```powershell
# Set your API key as an environment variable
$env:SPRING_AI_OPENAI_API_KEY = "sk-..."
```

To persist across sessions:
> Control Panel → System → Advanced System Settings → Environment Variables

Get a key at: <https://platform.openai.com/api-keys>

---

## Errors Encountered & How They Were Fixed

### Error 1 — SSL Certificate Failure (OpenAI behind corporate proxy)

**Symptom:**

```text
ResourceAccessException: I/O error on POST request for
"https://api.openai.com/v1/embeddings": (certificate_unknown) PKIX path building failed:
unable to find valid certification path to requested target
```

**Root cause:**

On app startup, `ApiDocumentIngestor` triggers `SimpleVectorStore` which calls
`OpenAiEmbeddingModel.dimensions()` → POSTs to `https://api.openai.com/v1/embeddings`.
A corporate SSL-inspection proxy (e.g., Zscaler, Netskope) intercepts the request and
re-signs the TLS certificate with its own CA. Java's `cacerts` truststore does not trust
that CA → SSL handshake fails.

**Fix — Import the corporate CA into the Java truststore:**

```powershell
keytool -import -alias corp-proxy-ca `
  -keystore "$env:JAVA_HOME\lib\security\cacerts" `
  -file "C:\path\to\corp-ca.cer" `
  -storepass changeit `
  -noprompt
```

Ask your IT/network team for the `.cer` file. Restart the app after importing.

**Alternative — Switch to Ollama** (no SSL issues since it runs on `localhost`).

---

### Error 2 — OpenAI bean created even when Ollama profile is active

**Symptom:**

```text
Error creating bean with name 'openAiEmbeddingModel': OpenAI API key must be set.
Use the connection property: spring.ai.openai.api-key
```

**Root cause (first occurrence):**

Only `spring.profiles.active=ollama` was set in `application.properties`, but the project
was not rebuilt with `-P ollama`. The Maven `openai` profile was `activeByDefault=true`, so
`spring-ai-starter-model-openai` stayed on the classpath and Spring Boot auto-configured
`openAiEmbeddingModel` unconditionally.

**Root cause (second occurrence — IDE run):**

After rebuilding with `mvn clean install -P ollama`, running the app from the **IDE** caused
the same error. IDEs resolve dependencies using the default Maven profile (`openai`),
ignoring the `-P ollama` used in the terminal build.

**Fix applied:**

Both provider JARs are now **always included** as permanent dependencies in
`agentic-docs-spring-boot-starter/pom.xml` — the Maven profile system was removed entirely.
Each Spring profile's properties file uses `spring.autoconfigure.exclude` to prevent the
inactive provider's beans from being created:

`application-ollama.properties`:

```properties
spring.autoconfigure.exclude=\
  org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration,\
  org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration
```

`application-openai.properties`:

```properties
spring.autoconfigure.exclude=\
  org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration,\
  org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration
```

---

### Error 3 — No EmbeddingModel bean found when using Ollama profile

**Symptom:**

```text
Parameter 0 of method vectorStore in VectorStoreConfig required a bean of type
'org.springframework.ai.embedding.EmbeddingModel' that could not be found.
```

**Root cause:**

After adding `spring.autoconfigure.exclude` for OpenAI in `application-ollama.properties`,
the project was rebuilt with the default Maven profile (no `-P`), which only included the
OpenAI JAR. The OpenAI auto-config was excluded → no `EmbeddingModel` bean existed at all.

**Fix applied:**

Same as Error 2 — moved both provider JARs into permanent `<dependencies>` in
`agentic-docs-spring-boot-starter/pom.xml`, removing the need for Maven profile selection.
Now both JARs are always present and `spring.autoconfigure.exclude` cleanly deactivates
the unused one.

---

### Warning — Ollama not running at startup

**Symptom (non-fatal, app still starts):**

```text
WARN  ApiDocumentIngestor : Vector store ingestion failed
(I/O error on POST request for "http://localhost:11434/api/embed": null).
API Explorer endpoints will still appear in the UI — only AI chat results may be affected.
```

**Root cause:**

Ollama server (`ollama serve`) was not running when the app started. The ingestor
gracefully catches the failure so the app still starts, but AI chat responses will not work.

**Fix:**

Start Ollama before starting the app:

```powershell
ollama serve
```

---

## Files Changed in This Fix

| File | What Changed |
| --- | --- |
| `agentic-docs-spring-boot-starter/pom.xml` | Removed Maven provider profiles; added both `spring-ai-starter-model-openai` and `spring-ai-starter-model-ollama` as permanent dependencies |
| `application-ollama.properties` | Added `spring.autoconfigure.exclude` for all OpenAI auto-configurations |
| `application-openai.properties` | Added `spring.autoconfigure.exclude` for all Ollama auto-configurations |
| `application.properties` | Changed `spring.profiles.active` from `openai` to `ollama` |
