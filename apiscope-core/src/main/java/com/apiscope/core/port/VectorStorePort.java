package com.apiscope.core.port;

import java.util.List;

/**
 * Domain port for context retrieval from the vector store.
 * Zero Spring AI imports — implemented by {@link com.apiscope.core.infrastructure.VectorStoreAdapter}.
 * To swap backends (SimpleVectorStore → PGVector, Chroma, etc.), write a new {@code @Primary} implementation.
 */
public interface VectorStorePort {

    /** Returns the top-K most semantically relevant text chunks for the given question. */
    List<String> findRelevantContext(String question, int topK);
}
