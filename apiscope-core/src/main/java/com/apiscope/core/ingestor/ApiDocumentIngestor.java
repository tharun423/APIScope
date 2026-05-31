package com.apiscope.core.ingestor;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.ApiScanCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens for {@link ApiScanCompletedEvent} and ingests discovered endpoints
 * into the {@link VectorStore} as embeddings for RAG similarity search.
 * Skips ingest if the vector store file already exists on disk.
 * Uses ObjectProvider so the app starts gracefully when no VectorStore is configured.
 */
@Component
public class ApiDocumentIngestor {

    private static final Logger log = LoggerFactory.getLogger(ApiDocumentIngestor.class);

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final AgenticDocsProperties properties;
    private final AtomicBoolean ingested = new AtomicBoolean(false);

    public ApiDocumentIngestor(ObjectProvider<VectorStore> vectorStoreProvider,
                                AgenticDocsProperties properties) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.properties          = properties;
    }

    @EventListener
    public void onScanCompleted(ApiScanCompletedEvent event) {
        if (!ingested.compareAndSet(false, true)) return;
        ingest(vectorStoreProvider.getIfAvailable(), event.endpoints(), false);
    }

    /**
     * Forces a full re-ingest regardless of whether the vector store file exists.
     * Deletes the existing file, resets the ingested flag, then re-embeds all endpoints.
     */
    public void reindex(List<ApiEndpointMetadata> endpoints) {
        new File(properties.vectorStorePath()).delete();
        ingested.set(false);
        ingest(vectorStoreProvider.getIfAvailable(), endpoints, true);
    }

    private void ingest(VectorStore vectorStore, List<ApiEndpointMetadata> endpoints, boolean forced) {
        if (!ingested.compareAndSet(false, true)) return;

        if (vectorStore == null) {
            log.info("[APIScope] No VectorStore available — skipping ingest. AI chat will not work.");
            return;
        }

        if (!forced && new File(properties.vectorStorePath()).exists()) {
            log.info("[APIScope] Vector store found on disk — skipping ingest.");
            return;
        }

        if (endpoints.isEmpty()) {
            log.warn("[APIScope] No endpoints found to ingest. Is the host app a @SpringBootApplication?");
            return;
        }

        List<Document> documents = endpoints.stream()
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
            vectorStore.add(documents);
            log.info("[APIScope] Ingested {} endpoint documents into the vector store.", documents.size());
        } catch (Exception ex) {
            log.warn("[APIScope] Vector store ingestion failed ({}). "
                    + "API Explorer still works; only AI chat results may be affected.",
                    ex.getMessage());
        }
    }
}
