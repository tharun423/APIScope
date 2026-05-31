# APIScope — Documentation Index

> **Version:** 1.0.0-SNAPSHOT  
> **Stack:** Java 21 · Spring Boot 3.4 · Spring AI 1.0.0 GA · React 18 · Tailwind CSS v4  
> **LLM Providers:** OpenAI (cloud) **or** Ollama (local/offline) — switchable via Spring profile  
> **Branch:** `both_openai_ollama`

---

## Documents

| File | What it covers |
|---|---|
| [01-project-overview.md](./01-project-overview.md) | What APIScope is, the problem it solves, core value proposition |
| [02-architecture.md](./02-architecture.md) | Module structure, component diagram, startup + chat data-flow |
| [03-backend-deep-dive.md](./03-backend-deep-dive.md) | Every Java class — design decisions and code walkthrough |
| [04-frontend-deep-dive.md](./04-frontend-deep-dive.md) | React component tree, UI decisions, build pipeline |
| [05-configuration-reference.md](./05-configuration-reference.md) | All `agentic.docs.*` properties, OpenAI and Ollama settings, environment variables |
| [06-getting-started.md](./06-getting-started.md) | Step-by-step: clone → configure → run → use |
| [07-engineering-thinking.md](./07-engineering-thinking.md) | Every trade-off and decision made during the build |
| [08-api-reference.md](./08-api-reference.md) | REST API contract for the chat and streaming endpoints |
| [09-extending-the-starter.md](./09-extending-the-starter.md) | Custom prompts, multi-turn, auth, contributing, roadmap |
| [10-switching-llm-providers.md](./10-switching-llm-providers.md) | Step-by-step guide for switching between OpenAI and Ollama |
| [11-ollama-local-setup.md](./11-ollama-local-setup.md) | Complete Ollama install + project setup on Windows |
| [12-bug-fixes.md](./12-bug-fixes.md) | Bug fix history and changelog |
| [13-endpoint-inputs-outputs.md](./13-endpoint-inputs-outputs.md) | Endpoint Inputs & Outputs panel — how it works, what it shows, how to extend it |
| [14-vector-database-enterprise-migration.md](./14-vector-database-enterprise-migration.md) | Migrating from SimpleVectorStore to PGVector / Redis for production |
| [15-switching-llm-providers-troubleshooting.md](./15-switching-llm-providers-troubleshooting.md) | Troubleshooting common LLM provider switch issues |
| [16-ollama-streaming-performance.md](./16-ollama-streaming-performance.md) | SSE streaming + Ollama tuning — eliminates wait time for local model responses |
| [17-critical-fixes.md](./17-critical-fixes.md) | Detailed engineering report for all critical fixes (Fixes #1–#7) |

---

## Quick Start — Ollama (Local / Free)

```bash
# 1. Install Ollama and pull models (one-time)
ollama pull llama3.2
ollama pull nomic-embed-text

# 2. Start Ollama
ollama serve

# 3. Build and run
mvn clean install -DskipTests
java -jar agentic-docs-sample-app/target/agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
```

Full details → [11-ollama-local-setup.md](./11-ollama-local-setup.md)

---

## Quick Start — OpenAI (Cloud)

```bash
# 1. Set your OpenAI API key
$env:SPRING_AI_OPENAI_API_KEY = "sk-..."    # PowerShell
# export SPRING_AI_OPENAI_API_KEY=sk-...   # macOS/Linux

# 2. Set active profile to openai
# In application.properties: spring.profiles.active=openai

# 3. Build and run
mvn clean install -DskipTests
java -jar agentic-docs-sample-app/target/agentic-docs-sample-app-1.0.0-SNAPSHOT.jar
```

Full switching guide → [10-switching-llm-providers.md](./10-switching-llm-providers.md)
