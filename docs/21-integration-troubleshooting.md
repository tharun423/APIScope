# 21 — Integration Troubleshooting: Using APIScope in External Spring Boot Services

This document captures every failure encountered when integrating `apiscope-spring-boot-starter` into an external Spring Boot microservice (`user-service`), the root cause of each failure, and the exact fix applied — both at the **consumer side** (application config) and the **library side** (apiscope source code).

---

## Overview

The `apiscope-spring-boot-starter` was designed and tested against the bundled `apiscope-sample-app`. When used in an **external** Spring Boot service pulled from Maven Central, several startup failures occurred due to:

- Missing dependencies
- Version mismatches
- Hard constructor injection of optional beans
- Broken XML in `pom.xml`

---

## Failure 1 — `VectorStorePort` bean not found (first occurrence)

### Error
```
Parameter 0 of constructor in com.apiscope.core.chat.AgenticDocsChatService
required a bean of type 'com.apiscope.core.port.VectorStorePort' that could not be found.
```

### Root Cause
The `apiscope-spring-boot-starter` marks `spring-ai-starter-model-ollama` as `<optional>true</optional>` — meaning it is **not transitively inherited** by consumer applications. Without the Ollama dependency on the classpath, no `EmbeddingModel` bean is created, which cascades:

```
No EmbeddingModel
  → VectorStoreConfig cannot create SimpleVectorStore
    → VectorStoreAdapter has no VectorStore to adapt
      → No VectorStorePort bean
        → AgenticDocsChatService constructor fails → CRASH
```

### Consumer-Side Fix
Add the Ollama dependency **explicitly** in your `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-ollama</artifactId>
</dependency>
```

And import the Spring AI BOM for version management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Failure 2 — Spring AI BOM not applied (broken `pom.xml` XML)

### Error
Same `VectorStorePort` not found error persisted after adding the dependency.

### Root Cause
The `<dependencyManagement>` block had **two separate `<dependencies>` child elements**, which is invalid Maven XML. Maven silently ignores the second block, so the Spring AI BOM was never imported and `spring-ai-starter-model-ollama` resolved to no version (build error or wrong version):

```xml
<!-- BROKEN — two <dependencies> blocks inside <dependencyManagement> -->
<dependencyManagement>
    <dependencies>
        <dependency><!-- spring-cloud-dependencies --></dependency>
    </dependencies>
    <dependencies>
        <dependency><!-- spring-ai-bom --></dependency>  ← IGNORED by Maven
    </dependencies>
</dependencyManagement>
```

### Fix
Merge both BOMs into a **single `<dependencies>` block**:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## Failure 3 — Wrong Ollama property keys in `application.properties`

### Error
Same `VectorStorePort` not found error even after dependency was correctly added.

### Root Cause
The consumer's `application.properties` used **incorrect Spring AI 1.0.0 property paths**:

```properties
# WRONG — these keys do not configure anything in Spring AI 1.0.0
spring.ai.ollama.embedding.model=nomic-embed-text
spring.ai.ollama.chat.model=llama3
```

Spring AI 1.0.0 uses `options.model`, not `model`:

```properties
# CORRECT
spring.ai.ollama.embedding.options.model=nomic-embed-text
spring.ai.ollama.chat.options.model=llama3.2
```

Without a valid embedding model, the Ollama auto-configuration skips `EmbeddingModel` bean registration entirely, reproducing the same cascade failure as Failure 1.

### Fix
Use a Spring profile (`application-ollama.properties`) with the correct property paths:

```properties
# application.properties
spring.profiles.active=ollama
apiscope.vector-store-path=./apiscope-vector-store.json

# application-ollama.properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.embedding.options.model=nomic-embed-text
```

---

## Failure 4 — Spring Boot version incompatibility

### Root Cause
The consumer used Spring Boot `3.3.3` while the starter was built and tested against Spring Boot `3.4.0`. Spring AI `1.0.0` auto-configurations use APIs introduced in Spring Boot `3.4.x`. On `3.3.x`, the Ollama `EmbeddingModel` auto-configuration silently skips, producing the same cascade failure.

| Component | user-service (broken) | apiscope-sample-app (working) |
|---|---|---|
| Spring Boot | `3.3.3` | `3.4.0` |
| Spring AI | `1.0.0` | `1.0.0` |
| Spring Cloud | `2023.0.3` | `2024.0.0` |

### Consumer-Side Fix
Upgrade Spring Boot and Spring Cloud to match:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.0</version>
</parent>

<properties>
    <spring-cloud.version>2024.0.0</spring-cloud.version>
</properties>
```

---

## Failure 5 — Lombok incompatibility with JDK 25

### Error
```
[ERROR] cannot find symbol: method getPhone()
[ERROR] cannot find symbol: method setAddress()
```

### Root Cause
The consumer application used **Lombok `1.18.30`** which does not support **JDK 25** (`openjdk-25.0.2`). Lombok annotation processing silently failed, so no getters/setters were generated despite `@Data` being present on the entity.

### Fix (Option A — Upgrade Lombok)
```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.36</version>
    <optional>true</optional>
</dependency>
```

### Fix (Option B — Remove Lombok, use plain Java)
Replace `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` with explicit getters, setters, and constructors. This is more portable and avoids annotation processor issues entirely.

---

## Library-Level Fixes (applied to apiscope source)

All the failures above exposed a fundamental design problem: the starter used **hard constructor injection** for optional Spring AI beans. If any bean in the chain was missing, the entire application context failed to start — even for consumers that don't use the AI chat feature at all.

### Fix 1 — `AgenticDocsAutoConfiguration`: Guard with `@ConditionalOnClass`

**File:** `apiscope-spring-boot-starter/.../AgenticDocsAutoConfiguration.java`

```java
@AutoConfiguration
@ConditionalOnWebApplication(type = SERVLET)
@ConditionalOnClass(name = "org.springframework.ai.embedding.EmbeddingModel")  // ← NEW
@ConditionalOnProperty(prefix = "apiscope", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgenticDocsProperties.class)
@ComponentScan(basePackages = "com.apiscope.core")
public class AgenticDocsAutoConfiguration {}
```

**Effect:** The entire auto-configuration skips gracefully if Spring AI is not on the classpath at all.

---

### Fix 2 — `VectorStoreConfig`: Add `@ConditionalOnBean(EmbeddingModel.class)`

**File:** `apiscope-core/.../config/VectorStoreConfig.java`

```java
@Bean
@ConditionalOnBean(EmbeddingModel.class)      // ← NEW
@ConditionalOnMissingBean(VectorStore.class)
public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) { ... }
```

**Effect:** `SimpleVectorStore` is only created when an `EmbeddingModel` bean actually exists.

---

### Fix 3 — `VectorStoreAdapter`: Use `ObjectProvider<VectorStore>`

**File:** `apiscope-core/.../infrastructure/VectorStoreAdapter.java`

```java
@Component
public class VectorStoreAdapter implements VectorStorePort {

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public VectorStoreAdapter(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @Override
    public List<String> findRelevantContext(String question, int topK) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) return List.of();   // ← graceful empty result
        // ... similarity search
    }
}
```

**Effect:** The adapter registers as a bean and returns empty context when no `VectorStore` is available, instead of crashing at startup.

---

### Fix 4 — `AgenticDocsChatService`: Use `ObjectProvider<VectorStorePort>`

**File:** `apiscope-core/.../chat/AgenticDocsChatService.java`

```java
@Service
public class AgenticDocsChatService implements ChatPort {

    private final ObjectProvider<VectorStorePort> vectorStorePortProvider;

    public AgenticDocsChatService(ObjectProvider<VectorStorePort> vectorStorePortProvider,
                                   LlmPort llmPort,
                                   AgenticDocsProperties properties) {
        this.vectorStorePortProvider = vectorStorePortProvider;
        // ...
    }

    private RagContext buildRagContext(String question) {
        VectorStorePort port = vectorStorePortProvider.getIfAvailable();
        List<String> chunks = (port != null)
                ? port.findRelevantContext(question, properties.topK())
                : List.of();   // ← returns empty context, chat still works
        // ...
    }
}
```

**Effect:** The service starts without a `VectorStorePort` bean. AI responses will lack RAG context but the application does not crash.

---

### Fix 5 — `ApiDocumentIngestor`: Use `ObjectProvider<VectorStore>`

**File:** `apiscope-core/.../ingestor/ApiDocumentIngestor.java`

```java
@Component
public class ApiDocumentIngestor {

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public ApiDocumentIngestor(ObjectProvider<VectorStore> vectorStoreProvider,
                                AgenticDocsProperties properties) {
        this.vectorStoreProvider = vectorStoreProvider;
        // ...
    }

    @EventListener
    public void onScanCompleted(ApiScanCompletedEvent event) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.info("[APIScope] No VectorStore available — skipping ingest.");
            return;   // ← graceful skip
        }
        // ... ingest documents
    }
}
```

**Effect:** Endpoint scanning and API Explorer still work. Only AI chat embedding is skipped when no vector store is configured.

---

## Summary: What Breaks and Why

| Failure | Cause | Library Fix | Consumer Fix |
|---|---|---|---|
| `VectorStorePort` not found | Ollama dep is `optional` in starter | `ObjectProvider` injection | Add `spring-ai-starter-model-ollama` explicitly |
| BOM not applied | Two `<dependencies>` blocks in `<dependencyManagement>` | N/A | Merge into one block |
| Wrong property keys | `embedding.model` vs `embedding.options.model` | N/A | Use `application-ollama.properties` profile |
| Spring Boot version | `3.3.3` incompatible with Spring AI `1.0.0` | N/A | Upgrade to `3.4.0` + Spring Cloud `2024.0.0` |
| Lombok failures | `1.18.30` does not support JDK 25 | N/A | Upgrade to `1.18.36` or remove Lombok |
| `VectorStore` not found in `ApiDocumentIngestor` | Hard constructor injection | `ObjectProvider` injection | N/A |

---

## Minimum Working `pom.xml` for External Consumer

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.0</version>
</parent>

<properties>
    <spring-cloud.version>2024.0.0</spring-cloud.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.tharun423</groupId>
        <artifactId>apiscope-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>
    <!-- your other dependencies -->
</dependencies>
```

## Minimum `application.properties` for External Consumer

```properties
spring.profiles.active=ollama
apiscope.vector-store-path=./apiscope-vector-store.json
```

## Minimum `application-ollama.properties` for External Consumer

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.embedding.options.model=nomic-embed-text
```

Pull required models once:
```bash
ollama pull llama3.2
ollama pull nomic-embed-text
```
