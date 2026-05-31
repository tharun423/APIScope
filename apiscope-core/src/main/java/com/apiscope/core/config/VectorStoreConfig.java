package com.apiscope.core.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * NOT picked up by @ComponentScan — registered explicitly via
 * AgenticDocsAutoConfiguration only when SimpleVectorStore is on the classpath.
 */
@Configuration(proxyBeanMethods = false)
public class VectorStoreConfig {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreConfig.class);

    private final AgenticDocsProperties properties;
    private SimpleVectorStore store;

    public VectorStoreConfig(AgenticDocsProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        store = SimpleVectorStore.builder(embeddingModel).build();
        File file = new File(properties.vectorStorePath());
        if (file.exists()) {
            store.load(file);
            log.info("[APIScope] Vector store loaded from {}", file.getAbsolutePath());
        } else {
            log.info("[APIScope] No vector store at '{}' — will ingest on startup.", file.getAbsolutePath());
        }
        return store;
    }

    @PreDestroy
    public void saveOnShutdown() {
        if (store == null) return;
        File file = new File(properties.vectorStorePath());
        try {
            store.save(file);
            log.info("[APIScope] Vector store saved to {}", file.getAbsolutePath());
        } catch (Exception ex) {
            log.warn("[APIScope] Could not save vector store: {}", ex.getMessage());
        }
    }
}
