# 14 — Vector Database: Current Implementation & Enterprise Migration Guide

---

## Part 1 — How Vector Database Is Implemented Today

### Overview

The current implementation uses **Spring AI's `SimpleVectorStore`** — an in-memory, zero-infrastructure vector store that requires no external database or configuration.

### Architecture at a Glance

```
Startup (ContextRefreshedEvent)
         │
         ▼
ApiMetadataScanner          ← @Order(1): scans all @RestController endpoints
         │
         ▼
ApiDocumentIngestor         ← @Order(2): converts endpoints → Documents → embeddings
         │
         ▼
VectorStore.add(documents)  ← Calls EmbeddingModel, stores float[] in HashMap (in-memory)

─────────────────────────────────────────────────────────────────────

Chat Request (POST /agentic-docs/api/chat)
         │
         ▼
AgenticDocsChatService
         │
         ├── vectorStore.similaritySearch(query, topK=5)
         │       ├── EmbeddingModel.embed(question) → query vector
         │       └── Cosine similarity (O(n)) against all stored float[] arrays
         │               → returns top-5 Document objects
         │
         └── ChatClient (OpenAI / Ollama) ← context injected into prompt
```

---

### Key Files

| File | Role |
|---|---|
| `VectorStoreConfig.java` | Declares the `SimpleVectorStore` bean with `@ConditionalOnMissingBean` |
| `ApiDocumentIngestor.java` | Converts API endpoints to `Document` objects and ingests into vector store |
| `AgenticDocsChatService.java` | Performs `similaritySearch()` to retrieve relevant context for RAG |

---

### `VectorStoreConfig.java` — The In-Memory Bean

```java
@Configuration
public class VectorStoreConfig {

    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
```

- `@ConditionalOnMissingBean` — **this is the enterprise hook**. If any other `VectorStore` bean is present (e.g., pgvector, Redis, Chroma), this fallback is automatically skipped.
- No external connection required — embeddings are stored as `float[]` in a `HashMap`.

---

### `ApiDocumentIngestor.java` — Embedding Pipeline

```java
@EventListener(ContextRefreshedEvent.class)
@Order(2)
public void ingest() {
    if (!ingested.compareAndSet(false, true)) return;  // idempotency guard

    List<Document> documents = scanner.getScannedEndpoints().stream()
            .map(e -> new Document(
                    e.toLlmReadableText(),
                    Map.of(
                        "path",       e.path(),
                        "httpMethod", e.httpMethod(),
                        "controller", e.controllerName(),
                        "method",     e.methodName()
                    )
            ))
            .toList();

    try {
        vectorStore.add(documents);  // calls EmbeddingModel internally
    } catch (Exception ex) {
        log.warn("Vector store ingestion failed: {}", ex.getMessage());
    }
}
```

- Each API endpoint is converted to a human-readable text chunk (`toLlmReadableText()`).
- Metadata (`path`, `httpMethod`, etc.) is stored alongside the embedding for retrieval.
- The `try-catch` ensures a failed embedding call does **not** crash the app.

---

### `AgenticDocsChatService.java` — Similarity Search

```java
List<Document> relevantDocs = vectorStore.similaritySearch(
        SearchRequest.builder()
                .query(request.question())
                .topK(properties.topK())   // default: 5
                .build()
);
```

- The user's question is embedded by the same `EmbeddingModel` used during ingestion.
- Top-K most semantically similar endpoint documents are retrieved.
- These documents form the RAG context injected into the LLM prompt.

---

### Current Limitations

| Limitation | Impact |
|---|---|
| **In-memory only** | Data lost on every restart — re-embedding required on each startup |
| **No deduplication** | Restarting always re-ingests the same documents |
| **O(n) search** | Slows down with thousands of documents |
| **Single JVM** | Cannot be shared across multiple app instances |
| **No persistence layer** | Cannot audit, version, or inspect stored embeddings |
| **No access control** | All embeddings are in the same heap |

---

## Part 2 — Enterprise-Level Migration Guide

### Supported Enterprise Vector Databases

Spring AI provides first-class support for the following stores via auto-configuration:

| Store | Best For | Type |
|---|---|---|
| **pgvector** | Teams already on PostgreSQL | Self-hosted / Cloud |
| **Redis Vector Search** | Teams already on Redis | Self-hosted / Cloud |
| **Chroma** | Local dev, lightweight production | Self-hosted |
| **Pinecone** | Fully managed, serverless | Cloud SaaS |
| **Weaviate** | Hybrid search, multi-modal | Self-hosted / Cloud |
| **Qdrant** | High performance, Rust-native | Self-hosted / Cloud |
| **Milvus** | Billion-scale vector workloads | Self-hosted / Cloud |
| **Azure AI Search** | Azure-native teams | Cloud SaaS |
| **Elasticsearch** | Teams already on ELK stack | Self-hosted / Cloud |

---

### Option A — pgvector (Recommended for Enterprise PostgreSQL Teams)

#### Step 1 — Run PostgreSQL with pgvector

```bash
docker run -d \
  --name pgvector \
  -e POSTGRES_USER=agentic \
  -e POSTGRES_PASSWORD=secret \
  -e POSTGRES_DB=agenticdocs \
  -p 5432:5432 \
  pgvector/pgvector:pg16
```

#### Step 2 — Replace dependency in `agentic-docs-spring-boot-starter/pom.xml`

Remove the simple vector store and add pgvector:

```xml
<!-- REMOVE this -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-simple-vector-store</artifactId>
</dependency>

<!-- ADD this -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
</dependency>

<!-- Also add the JDBC driver -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

#### Step 3 — Add properties to `application.properties`

```properties
# PostgreSQL connection
spring.datasource.url=jdbc:postgresql://localhost:5432/agenticdocs
spring.datasource.username=agentic
spring.datasource.password=secret

# pgvector auto-creates the vector table
spring.ai.vectorstore.pgvector.initialize-schema=true
spring.ai.vectorstore.pgvector.dimensions=1536        # match your embedding model
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.index-type=HNSW        # fast approximate search
```

> **Note:** `VectorStoreConfig.java` does not need to be changed. The `@ConditionalOnMissingBean` annotation means the pgvector starter's auto-provided `VectorStore` bean takes precedence automatically.

#### Step 4 — Prevent re-ingestion on restart (optional but recommended)

Add a deduplication check in `ApiDocumentIngestor.java`:

```java
@EventListener(ContextRefreshedEvent.class)
@Order(2)
public void ingest() {
    if (!ingested.compareAndSet(false, true)) return;

    // Check if data already exists in the persistent store
    List<Document> existing = vectorStore.similaritySearch(
            SearchRequest.builder().query("GET").topK(1).build()
    );
    if (!existing.isEmpty()) {
        log.info("[AgenticDocs] Vector store already populated. Skipping ingestion.");
        return;
    }

    // ... rest of ingestion logic
}
```

---

### Option B — Redis Vector Search

#### Step 1 — Run Redis Stack

```bash
docker run -d \
  --name redis-vector \
  -p 6379:6379 \
  redis/redis-stack:latest
```

#### Step 2 — Replace dependency

```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-simple-vector-store</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-redis-store-spring-boot-starter</artifactId>
</dependency>
```

#### Step 3 — Add properties

```properties
spring.data.redis.host=localhost
spring.data.redis.port=6379

spring.ai.vectorstore.redis.initialize-schema=true
spring.ai.vectorstore.redis.index=agentic-docs-index
spring.ai.vectorstore.redis.prefix=agentic:docs:
```

---

### Option C — Chroma (Lightweight, Already Running in This Repo)

> The terminal history shows `docker run -p 8000:8000 chromadb/chroma` was recently attempted. Here's how to connect the app to it.

#### Step 1 — Run Chroma

```bash
docker run -d \
  --name chromadb \
  -p 8000:8000 \
  -v chroma-data:/chroma/chroma \
  chromadb/chroma
```

#### Step 2 — Replace dependency

```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-simple-vector-store</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-chroma-store-spring-boot-starter</artifactId>
</dependency>
```

#### Step 3 — Add properties

```properties
spring.ai.vectorstore.chroma.client.host=http://localhost
spring.ai.vectorstore.chroma.client.port=8000
spring.ai.vectorstore.chroma.collection-name=agentic-docs
spring.ai.vectorstore.chroma.initialize-schema=true
```

---

### Option D — Pinecone (Fully Managed, Zero Infrastructure)

#### Step 1 — Create account and index at [pinecone.io](https://pinecone.io)

- Create an index named `agentic-docs`
- Set dimensions to `1536` (OpenAI `text-embedding-3-small`) or `768` (Ollama `nomic-embed-text`)
- Select metric: `cosine`

#### Step 2 — Replace dependency

```xml
<!-- REMOVE -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-simple-vector-store</artifactId>
</dependency>

<!-- ADD -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-pinecone-store-spring-boot-starter</artifactId>
</dependency>
```

#### Step 3 — Add properties

```properties
spring.ai.vectorstore.pinecone.api-key=${PINECONE_API_KEY}
spring.ai.vectorstore.pinecone.index-name=agentic-docs
spring.ai.vectorstore.pinecone.namespace=production
```

---

## Part 3 — Enterprise Architecture Enhancements

Beyond just swapping the vector store, a production deployment requires:

### 1 — Embedding Dimension Alignment

The dimension size **must match** between your embedding model and the vector store index:

| Embedding Model | Dimensions |
|---|---|
| OpenAI `text-embedding-3-small` | 1536 |
| OpenAI `text-embedding-3-large` | 3072 |
| Ollama `nomic-embed-text` | 768 |
| Ollama `mxbai-embed-large` | 1024 |

Set `spring.ai.vectorstore.<store>.dimensions` to match exactly.

---

### 2 — Deduplication & Incremental Updates

The current `ApiDocumentIngestor` re-ingests all endpoints on every restart. For persistent stores, add a hash-based or timestamp-based check:

```java
// Compute a fingerprint of all endpoints
String fingerprint = endpoints.stream()
        .map(e -> e.path() + e.httpMethod())
        .sorted()
        .collect(Collectors.joining("|"));

// Store fingerprint in a simple table or cache
// Only re-ingest if fingerprint has changed
```

---

### 3 — HNSW Indexing

For large document sets (>10,000), switch from flat exact search to HNSW (Hierarchical Navigable Small World) approximate nearest neighbor indexing. pgvector supports this natively:

```properties
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.hnsw-m=16
spring.ai.vectorstore.pgvector.hnsw-ef-construction=64
```

---

### 4 — Multi-Tenancy

For SaaS products serving multiple teams, add a tenant identifier to the metadata and use metadata filter expressions during search:

```java
// During ingestion — add tenant to metadata
Map.of(
    "path",       e.path(),
    "httpMethod", e.httpMethod(),
    "tenantId",   tenantId       // ← add this
)

// During search — filter by tenant
SearchRequest.builder()
    .query(request.question())
    .topK(5)
    .filterExpression("tenantId == '" + tenantId + "'")
    .build()
```

---

### 5 — Observability

Add metrics and tracing to the vector store operations:

```java
// Wrap vectorStore with Micrometer tracing
@Bean
public VectorStore vectorStore(PgVectorStore pgVectorStore, MeterRegistry meterRegistry) {
    return new ObservationVectorStore(pgVectorStore, ObservationRegistry.create());
}
```

Add to `application.properties`:

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

---

### 6 — Security

- Store credentials in **environment variables** or **Vault** — never hardcode in `application.properties`
- Use **Spring Cloud Vault** or **AWS Secrets Manager** for secret rotation
- Apply **network-level ACLs** to restrict database access to the app's service account only
- Enable **TLS** on all vector database connections

```properties
# Use environment variable substitution
spring.datasource.password=${PGVECTOR_PASSWORD}
spring.ai.openai.api-key=${OPENAI_API_KEY}
```

---

## Part 4 — Quick Comparison Summary

| Feature | Current (SimpleVectorStore) | Enterprise (pgvector / Pinecone / Redis) |
|---|---|---|
| **Persistence** | ❌ Lost on restart | ✅ Durable |
| **Scale** | ~10K docs | Millions of docs |
| **Multi-instance** | ❌ Per-JVM only | ✅ Shared across replicas |
| **Search algorithm** | O(n) cosine | HNSW / IVFFlat (ANN) |
| **Code change required** | — | Only `pom.xml` + `application.properties` |
| **Infrastructure** | None | Docker / Cloud DB |
| **Deduplication** | Re-ingests every restart | Can be fingerprint-checked |
| **Observability** | None | Metrics, tracing, dashboards |
| **Multi-tenancy** | ❌ | ✅ via metadata filters |
| **Access control** | ❌ | ✅ DB-level ACLs |

---

## Part 5 — Migration Checklist

```
[ ] 1. Choose target vector store (pgvector recommended for most enterprise teams)
[ ] 2. Provision the database (Docker / Cloud / managed service)
[ ] 3. Replace dependency in pom.xml
[ ] 4. Add connection properties to application.properties (use env vars for secrets)
[ ] 5. Set correct embedding dimensions to match your embedding model
[ ] 6. Enable schema initialization (spring.ai.vectorstore.<store>.initialize-schema=true)
[ ] 7. Remove or skip VectorStoreConfig.java bean (auto-suppressed by @ConditionalOnMissingBean)
[ ] 8. Add deduplication logic to ApiDocumentIngestor (avoid re-embedding on every restart)
[ ] 9. Switch to HNSW indexing if document count exceeds 10,000
[ ] 10. Add metadata filters for multi-tenancy (if applicable)
[ ] 11. Set up Prometheus / Grafana dashboard for vector store latency
[ ] 12. Store all secrets in Vault or environment variables
[ ] 13. Enable TLS on database connection
[ ] 14. Run load test: confirm topK=5 search latency < 50ms under concurrent load
```

---

> **No application logic changes are required.** The `VectorStore` interface is the abstraction. `ApiDocumentIngestor`, `AgenticDocsChatService`, and all RAG logic remain identical regardless of which store is chosen. Only the dependency and configuration change.
