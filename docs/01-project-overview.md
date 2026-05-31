# 01 — Project Overview

## What is Agentic Docs?

Agentic Docs is a **Spring Boot Starter** that transforms the static, hard-to-navigate API documentation of any Spring Boot application into an interactive, conversational AI agent.

Instead of a developer opening Swagger UI and manually reading through 150 endpoints to find the right one, they open a chat interface and ask:

> *"How do I cancel a premium subscription but only trigger a partial refund if the user has been active for less than 15 days?"*

The agent reads the actual endpoints registered in the running application, retrieves the most relevant ones using vector similarity search, and responds with a precise, context-aware answer — including working Java or React code snippets.

---

## The Problem It Solves

### Traditional API Documentation Workflow

In large companies, internal APIs grow to hundreds of endpoints. The typical developer experience looks like this:

1. Open Swagger UI — find 5 endpoints matching "cancel"
2. Spend 45 minutes reading each schema to find which one handles refund eligibility
3. Manually construct a JSON payload — get a `400 Bad Request` because a hidden header was required
4. **3 hours wasted** on research and debugging

This is not a tooling problem. It is a **knowledge retrieval problem**. The information exists — it is just buried in static HTML.

### The Agentic Docs Solution

Agentic Docs embeds a **RAG (Retrieval-Augmented Generation) agent** directly into the application. The agent:

- Automatically discovers every `@RestController` endpoint at startup
- Converts endpoint metadata into vector embeddings stored in memory
- Accepts natural language questions via a chat UI served at `/agentic-docs/`
- Retrieves the top-5 most semantically relevant endpoints for each question
- Injects that context into a strict system prompt and calls an LLM
- Returns a focused, accurate answer grounded only in the actual API

---

## Core Value Proposition

| Without Agentic Docs | With Agentic Docs |
|---|---|
| Read 150 Swagger entries manually | Ask one question in plain English |
| Trial-and-error with request bodies | Get the exact request shape from context |
| Copy-paste boilerplate from Stack Overflow | Get code generated from your actual endpoints |
| Click an endpoint in Swagger — see no param types | Click an endpoint — see path params, query params, request body, and response type instantly |
| 3 hours of research | 30 seconds |

---

## Who Is It For?

- **New developers** onboarding onto a large microservice — they can ask questions instead of reading docs
- **Senior developers** who need to quickly recall the exact shape of an endpoint they haven't touched in months
- **Teams** who want to add AI-assisted documentation to their internal APIs with zero infrastructure cost

---

## Key Design Constraints

The project was built with these non-negotiable constraints:

1. **Zero infrastructure** — no external database, no Docker, no Redis. The vector store lives in JVM memory.
2. **Plug-and-play** — adding the starter to any Spring Boot app requires exactly one dependency and one property.
3. **Non-invasive** — the starter does not modify the host application's beans, routes, or configuration.
4. **Self-contained UI** — the React frontend is pre-built and served as static resources by Spring Boot itself.
