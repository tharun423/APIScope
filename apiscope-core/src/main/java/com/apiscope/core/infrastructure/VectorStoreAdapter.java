package com.apiscope.core.infrastructure;

import com.apiscope.core.port.VectorStorePort;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapts Spring AI's {@link VectorStore} to the {@link VectorStorePort} interface.
 * Uses ObjectProvider so the adapter starts even when no VectorStore bean is available.
 * To swap vector store backends (SimpleVectorStore → PGVector, Redis, etc.),
 * write a new {@code @Primary} {@link VectorStorePort} implementation.
 */
@Component
public class VectorStoreAdapter implements VectorStorePort {

    private final ObjectProvider<VectorStore> vectorStoreProvider;

    public VectorStoreAdapter(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
    }

    @Override
    public List<String> findRelevantContext(String question, int topK) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) return List.of();
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .build()
        );
        return docs.stream()
                .map(Document::getText)
                .toList();
    }
}
