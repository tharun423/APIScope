# 11 — Setting Up Ollama Locally (Windows)

This guide takes you from zero to a fully running offline Agentic Docs instance
using Ollama on your Windows machine. No API key. No internet after setup. No cost.

---

## What You Will Have After This Guide

```
Your Machine
├── Ollama (background service on port 11434)
│   ├── llama3.2          ← answers developer questions
│   └── nomic-embed-text  ← converts API endpoints into vectors
└── Agentic Docs (Spring Boot on port 8080)
    └── http://localhost:8080/agentic-docs/  ← chat UI, fully offline
```

---

## Prerequisites Check

Before starting, confirm you have Java 21 and Maven installed:

```cmd
java -version
mvn -version
```

If either is missing, install them before continuing:
- Java 21: https://adoptium.net
- Maven 3.9+: https://maven.apache.org/download.cgi

---

## Step 1 — Install Ollama

### Option A — Download installer manually

1. Go to **https://ollama.com/download**
2. Click **Download for Windows**
3. Run `OllamaSetup.exe`
4. Follow the installer (no configuration needed, just Next → Install)

### Option B — Download via PowerShell

```powershell
Invoke-WebRequest -Uri "https://ollama.com/download/OllamaSetup.exe" -OutFile "$env:TEMP\OllamaSetup.exe"
Start-Process "$env:TEMP\OllamaSetup.exe" -Wait
```

### Verify installation

Open a **new** Command Prompt (important — PATH needs to refresh) and run:

```cmd
ollama --version
```

Expected output:
```
ollama version 0.x.x
```

> Ollama installs as a Windows background service and starts automatically.
> You will see the Ollama icon in your system tray (bottom-right of taskbar).

---

## Step 2 — Pull the Required Models

These are one-time downloads. The models are stored locally and reused on every run.

### Pull the chat model (~2 GB)

```cmd
ollama pull llama3.2
```

You will see a progress bar. This takes 2–10 minutes depending on your internet speed.

### Pull the embedding model (~274 MB)

```cmd
ollama pull nomic-embed-text
```

### Verify both models are ready

```cmd
ollama list
```

Expected output:
```
NAME                    ID              SIZE    MODIFIED
llama3.2:latest         a80c4f17acd5    2.0 GB  X minutes ago
nomic-embed-text:latest 0a109f422b47    274 MB  X minutes ago
```

Both must appear before continuing.

---

## Step 3 — Confirm Ollama is Serving

```cmd
curl http://localhost:11434
```

Expected response:
```
Ollama is running
```

If you get `Connection refused`, start Ollama manually:
```cmd
ollama serve
```

Then re-run the curl check.

---

## Step 4 — Switch the Project to Ollama

Open this file in any text editor:

```
agentic-docs-sample-app\src\main\resources\application.properties
```

Find this line:

```properties
spring.profiles.active=openai
```

Change it to:

```properties
spring.profiles.active=ollama
```

Save the file. That is the **only file** you need to edit.

> The full Ollama configuration (model names, base URL) is already set up in
> `application-ollama.properties` — you do not need to touch that file.

---

## Step 5 — Build the UI

```cmd
cd "d:\open source\Agentic-Docs\agentic-docs-ui"
npm install
npm run build
cd ..
```

> Skip this step if you have already built the UI previously and made no UI changes.

---

## Step 6 — Rebuild the Maven Project with the Ollama Profile

```cmd
cd "d:\open source\Agentic-Docs"
mvn clean install -P ollama -DskipTests
```

The `-P ollama` flag activates the Ollama Maven profile in the starter POM,
which puts `spring-ai-ollama-spring-boot-starter` on the classpath instead of
the OpenAI one.

Expected output at the end:
```
[INFO] BUILD SUCCESS
```

---

## Step 7 — Run the Application

```cmd
java -jar agentic-docs-sample-app\target\agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
```

### What to look for in the startup logs

```
INFO  c.a.d.c.scanner.ApiMetadataScanner   : [AgenticDocs] Scanned 8 REST endpoints for RAG indexing.
INFO  c.a.d.c.ingestor.ApiDocumentIngestor : [AgenticDocs] Ingested 8 endpoint documents into the vector store.
```

These two lines confirm that:
1. The scanner found your REST endpoints
2. The embeddings were created using `nomic-embed-text` and stored in memory

---

## Step 8 — Open the Chat UI

Navigate to: **http://localhost:8080/agentic-docs/**

Try asking:
- *"How do I cancel a subscription with a partial refund?"*
- *"Generate Java code to process a payment"*
- *"What endpoints are available?"*

All responses come from `llama3.2` running on your machine. No data leaves your computer.

---

## Choosing the Right Model for Your Hardware

The default `llama3.2` (3B parameters) works on most laptops. If responses are
slow or you run out of memory, use a smaller model.

| Your RAM | Recommended Chat Model | Pull Command | Quality |
|---|---|---|---|
| 8 GB | `llama3.2:1b` | `ollama pull llama3.2:1b` | Good |
| 16 GB | `llama3.2` ✓ default | already pulled | Better |
| 32 GB+ | `llama3.1:8b` | `ollama pull llama3.1:8b` | Best |

### To switch to a different model

1. Pull it:
   ```cmd
   ollama pull llama3.2:1b
   ```

2. Edit `application-ollama.properties`:
   ```properties
   spring.ai.ollama.chat.options.model=llama3.2:1b
   ```

3. Restart the app (no rebuild needed for model changes):
   ```cmd
   java -jar agentic-docs-sample-app\target\agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
   ```

---

## Verify the Test API Call (Optional)

Test the backend directly without the UI:

```cmd
curl -X POST http://localhost:8080/agentic-docs/api/chat ^
  -H "Content-Type: application/json" ^
  -d "{\"question\": \"What endpoints are available?\"}"
```

Expected: a JSON response with an `answer` field containing text from `llama3.2`.

---

## Troubleshooting

### Ollama not found after install

```
'ollama' is not recognized as an internal or external command
```

**Fix:** Close and reopen Command Prompt so the PATH refreshes. If still not found,
restart your machine.

---

### Connection refused on port 11434

```
curl: (7) Failed to connect to localhost port 11434
```

**Fix:** Ollama is not running. Start it:
```cmd
ollama serve
```

Or find the Ollama icon in the system tray and click "Start".

---

### Model not found at startup

```
model "llama3.2" not found, try pulling it first
```

**Fix:**
```cmd
ollama pull llama3.2
ollama pull nomic-embed-text
```

---

### Out of memory / app crashes

**Fix:** Switch to the 1B model:
```cmd
ollama pull llama3.2:1b
```
Edit `application-ollama.properties`:
```properties
spring.ai.ollama.chat.options.model=llama3.2:1b
```
Restart the app.

---

### Slow first response (30+ seconds)

This is normal on the very first request after startup — Ollama loads the model
into memory. Subsequent requests are much faster (2–5 seconds).

The `keep-alive=5m` setting in `application-ollama.properties` keeps the model
loaded for 5 minutes after the last request, so you won't hit this delay again
unless the app is idle for more than 5 minutes.

---

### BUILD FAILURE — NoSuchBeanDefinitionException: ChatModel

```
NoSuchBeanDefinitionException: No qualifying bean of type 'ChatModel'
```

**Cause:** You changed `spring.profiles.active=ollama` but forgot to rebuild
with `-P ollama`.

**Fix:**
```cmd
mvn clean install -P ollama -DskipTests
```

---

## Switching Back to OpenAI

When you want to go back to the cloud provider:

1. Edit `application.properties`:
   ```properties
   spring.profiles.active=openai
   ```

2. Set your API key:
   ```cmd
   set SPRING_AI_OPENAI_API_KEY=sk-your-key-here
   ```

3. Rebuild:
   ```cmd
   mvn clean install -DskipTests
   ```

Full details → [10-switching-llm-providers.md](./10-switching-llm-providers.md)

---

## Complete Command Summary

Copy-paste this entire block to go from zero to running:

```cmd
REM ── Step 1: Install Ollama (do manually from https://ollama.com/download) ──

REM ── Step 2: Pull models ───────────────────────────────────────────────────
ollama pull llama3.2
ollama pull nomic-embed-text

REM ── Step 3: Verify Ollama is running ─────────────────────────────────────
curl http://localhost:11434

REM ── Step 4: Switch profile (edit application.properties manually) ─────────
REM   spring.profiles.active=ollama

REM ── Step 5: Build UI ──────────────────────────────────────────────────────
cd "d:\open source\Agentic-Docs\agentic-docs-ui"
npm install
npm run build

REM ── Step 6: Build Maven project ───────────────────────────────────────────
cd "d:\open source\Agentic-Docs"
mvn clean install -P ollama -DskipTests

REM ── Step 7: Run ───────────────────────────────────────────────────────────
java -jar agentic-docs-sample-app\target\agentic-docs-sample-app-1.0.0-SNAPSHOT.jar

REM ── Step 8: Open browser ──────────────────────────────────────────────────
REM   http://localhost:8080/agentic-docs/
```
