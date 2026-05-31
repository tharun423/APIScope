
**APIScope** is a Spring Boot starter that turns static API documentation (like Swagger) into an interactive AI Agent — powered by RAG (Retrieval-Augmented Generation). It also provides a real-time **Flow Tracer** that visualizes every method call in your application as it executes.

---

## Table of Contents
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Setup & Running](#setup--running)
- [Running the Sample App](#running-the-sample-app)
- [Running the UI (Development)](#running-the-ui-development)
- [Accessing the Agent](#accessing-the-agent)
- [API Endpoints](#api-endpoints)
- [What's New](#whats-new)
- [Configuration Reference](#configuration-reference)
- [Known Issues & Fixes](#known-issues--fixes)

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21+ | [Download](https://adoptium.net/) |
| Maven | 3.9+ | [Download](https://maven.apache.org/download.cgi) |
| Node.js | 18+ | Only needed for UI development |
| Ollama | Latest | Only needed for local LLM — [Download](https://ollama.com/download) |



---

## Project Structure

```
agentic-docs/
├── agentic-docs-core/                 # Core RAG scanning & chat logic
├── agentic-docs-spring-boot-starter/  # Auto-configuration & bundled UI
├── agentic-docs-sample-app/           # Runnable demo application
├── agentic-docs-ui/                   # React frontend source (Vite)
└── docs/                              # Architecture & engineering docs
```

---

## Setup & Running

### 1. Clone the Repository

```bash
git clone https://github.com/tharun423/APIScope.git
cd Agentic-Docs
```

### 2. Build the Project

```bash
mvn clean install -DskipTests
```

This builds all modules in order: `core` → `spring-boot-starter` → `sample-app`.

---

### 3. Run with Ollama (Local, Free)

> Runs entirely on your machine — no API key, no cost, no SSL issues.

**Step 1:** Install Ollama from [https://ollama.com/download](https://ollama.com/download)

**Step 2:** Pull the required models (one-time setup):
```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```

**Step 3:** Start Ollama:
```bash
ollama serve
```

**Step 4:** Run the sample app:
```bash
cd agentic-docs-sample-app
mvn spring-boot:run
```

---

## Running the Sample App

Once started, the backend runs on **http://localhost:8080**.

You can also pass the profile at runtime:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=ollama
```

---

## Running the UI (Development)

The starter ships with a **pre-built UI** embedded in the JAR — no Node.js needed to run the app.

For UI development only:

```bash
cd agentic-docs-ui
npm install
npm run dev
```

The dev UI runs on **http://localhost:5173** and proxies API calls to `http://localhost:8080`.

To build and bundle the UI back into the starter:
```bash
npm run build
```

---

## Accessing the Agent

| URL | Description |
|-----|-------------|
| `http://localhost:8080/` | Redirects to the Agentic Docs UI |
| `http://localhost:8080/agentic-docs` | APIScope UI — AI Chat + API Explorer + Flow Tracer |
| `http://localhost:8080/agentic-docs/` | Same as above (trailing slash) |
| `http://localhost:8080/swagger-ui.html` | Swagger UI for the sample app |
| `http://localhost:8080/v3/api-docs` | Raw OpenAPI JSON spec |

---

## API Endpoints

### Agentic Docs REST API

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/agentic-docs/api/endpoints` | Returns JSON list of all scanned REST endpoints (includes `pathParams`, `queryParams`, `requestBodyType`, `responseType`) |
| `POST` | `/agentic-docs/api/chat` | RAG chat — body: `{"question": "..."}`, response: `{"answer": "..."}` |
| `GET` | `/agentic-docs/api/chat` | Returns a helpful message (endpoint only accepts POST) |

### Sample App Endpoints (PaymentsController)

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/api/v1/subscriptions/{id}` | Get subscription details by ID |
| `GET` | `/api/v1/accounts/{accountId}/subscriptions` | List all subscriptions for an account |
| `POST` | `/api/v1/subscriptions/{id}/terminate` | Terminate a subscription with refund type |
| `PUT` | `/api/v1/subscriptions/{id}/plan` | Upgrade or downgrade a subscription plan |
| `POST` | `/api/v1/billing/refund/calculate` | Calculate refund amount |
| `POST` | `/api/v1/payments/process` | Process a payment |
| `GET` | `/api/v1/accounts/{accountId}/payments` | Get paginated payment history |
| `POST` | `/api/v1/payments/refund` | Issue a manual refund |

---

## What's New

### v1.1 — Endpoint Inputs & Outputs Panel

When you click any endpoint in the **API Explorer** tab, the expanded panel now shows a rich **Inputs & Outputs** section:

| Field | Color | What it shows |
|---|---|---|
| **Path Params** | 🟡 Amber | `{id}`, `{accountId}` — path variables extracted from `@PathVariable` |
| **Query Params** | 🔵 Sky | `?page`, `?size` — query strings extracted from `@RequestParam` |
| **Request Body** | 🟢 Emerald | DTO class name extracted from `@RequestBody` (e.g. `CreateUserRequest`) |
| **Response** | 🟣 Violet | Return type, unwrapped from `ResponseEntity<T>` (e.g. `UserResponse`) |

This data is extracted **automatically at startup** from your controller method signatures — no annotations or extra config required.

The same fields are also injected into the LLM context, so the AI gives more accurate answers about request shapes and response types.

> Full details: [`docs/13-endpoint-inputs-outputs.md`](docs/13-endpoint-inputs-outputs.md)

---

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `spring.profiles.active` | `ollama` | LLM provider (Ollama only) |
| `agentic.docs.enabled` | `true` | Enable/disable the RAG agent |
| `spring.ai.ollama.base-url` | `http://localhost:11434` | Ollama server URL |
| `spring.ai.ollama.chat.options.model` | `llama3.2` | Ollama chat model |
| `spring.ai.ollama.embedding.options.model` | `nomic-embed-text` | Ollama embedding model |

---


| 2 | `NoSuchMethodError` on `setObservationConvention` | Removed hardcoded `spring-ai-core:1.0.0-M6` — all versions now governed by `spring-ai-bom:1.0.0` |
| 3 | `spring-ai-core:1.0.0` not found on Maven Central | Replaced with `spring-ai-model` + `spring-ai-vector-store` + `spring-ai-client-chat` (GA split artifacts) |
| 4 | HTTP 405 on `/agentic-docs/api/chat` | Added `WebMvcConfigurer` with CORS + `@Qualifier` on `RequestMappingHandlerMapping` + `GET /chat` fallback handler |
| 5 | HTTP 404 on `/agentic-docs` (no trailing slash) | Added `forward:/agentic-docs/index.html` view controllers for `/`, `/agentic-docs`, `/agentic-docs/` |
| 6 | Endpoints not appearing in API Explorer | Fixed overly broad package filter (`com.agentic.docs` → specific internal packages only) |
| 7 | Ingestor ran before scanner (empty endpoint list) | Fixed `@Order`: scanner `@Order(1)`, ingestor `@Order(2)` |
| 8 | App crash on startup due to SSL/TLS error | Wrapped `vectorStore.add()` in try-catch — API Explorer always works even if AI chat cannot connect |

---

## What is it?

Standard API docs are just lists of endpoints. **APIScope** embeds a **RAG (Retrieval-Augmented Generation) Agent** directly into your browser, and adds a **Flow Tracer** tab that streams real-time Spring AOP execution events as you call your endpoints. Instead of searching through long tables, you can ask the documentation questions in plain English — and watch the full call chain light up live.

### The Core Idea
If a developer wants to know:
> *"How do I implement a partial refund using these APIs?"*

The agent reads your project's REST endpoints and generates the specific **React** or **Java** code snippets needed to make it work.

### Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21 + Spring Boot 3.4.0 |
| AI / RAG | Spring AI 1.0.0 GA |
| LLM (cloud) | OpenAI `gpt-4o-mini` |
| LLM (local) | Ollama `llama3.2` + `nomic-embed-text` |
| Vector Store | Spring AI `SimpleVectorStore` (in-memory) |
| Frontend | React 18 + Vite + Tailwind CSS |

### Why use this?
In large companies, developers waste hours trying to understand complex internal APIs. This project solves that by creating a "Pair Programmer" that lives inside your documentation.

---

*Built for the 2026 Open Source Community.* | **APIScope** by [@tharun423](https://github.com/tharun423)

---

## Use Case: Streamlining Microservice Integration

### The Environment
Imagine a senior developer working on a "Payments & Ledger" microservice. The service has over **150+ endpoints**, complex DTO inheritance, and legacy documentation that hasn't been updated in months.

### The Problem (Traditional Workflow)
A new developer needs to implement a **Conditional Subscription Cancellation**.
1. **Search:** They open Swagger and search for "cancel." They find 5 different endpoints.
2. **Analysis:** They spend 45 minutes reading the schema for each to see which one handles "Refund Eligibility."
3. **Trial & Error:** They manually build a JSON payload, but the API returns a `400 Bad Request` because a hidden header was required.
4. **Outcome:** 3 hours wasted on research and debugging.

---

### The Solution (Agentic Docs Workflow)
The developer opens `http://localhost:8080/agentic-docs` powered by the **APIScope** Spring Boot starter.

#### 1. Contextual Inquiry
The developer types into the embedded chat:
> *"How do I cancel a premium subscription but only trigger a partial refund if the user has been active for less than 15 days?"*

#### 2. Agentic Reasoning
The **APIScope** engine performs the following:
- **Vector Search:** Finds the `/subscriptions/{id}/terminate` and `/billing/refund/calculate` endpoints.
- **Context Injection:** Reads the endpoint summaries and request body schemas from the indexed documents.
- **Logic Synthesis:** Combines the two endpoints to produce a complete implementation.

#### 3. Instant Implementation
The agent responds with a step-by-step guide and code:
> "To handle this, you need to call the **Termination API** followed by the **Refund API**. Here is your implementation logic:"

```java
// Generated by APIScope Agent
public void handleSmartCancellation(String subId) {
    Subscription sub = subscriptionClient.getDetails(subId);

    // Logic based on your 15-day requirement
    boolean isEligible = sub.getDaysActive() < 15;

    TerminationRequest request = new TerminationRequest();
    request.setRefundType(isEligible ? "PARTIAL" : "NONE");

    apiClient.terminateSubscription(subId, request);
}
```
