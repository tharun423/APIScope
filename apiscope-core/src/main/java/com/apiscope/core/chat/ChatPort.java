package com.apiscope.core.chat;

import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * Unified port for the chat pipeline.
 * The controller depends only on this interface — no AI infrastructure details leak into HTTP handling.
 * To provide a custom implementation (caching, A/B routing, etc.), declare a {@code @Primary @Bean ChatPort}.
 */
public interface ChatPort {

    /** Returns the LLM-generated answer for a question (blocking). */
    ChatResponse answer(ChatRequest request);

    /** Streams the LLM answer token-by-token (reactive). */
    Flux<String> streamAnswer(ChatRequest request);
}
