package com.apiscope.core.port;

import reactor.core.publisher.Flux;

/**
 * Domain port for LLM interaction. Isolates the service from Spring AI's ChatClient.
 * Implemented by {@link com.apiscope.core.infrastructure.LlmAdapter}.
 * To swap LLM providers, write a new {@code @Primary} implementation — no service changes needed.
 */
public interface LlmPort {

    /** Sends a prompt to the LLM and waits for the full response (blocking). */
    String complete(String systemPromptTemplate, String context, String question);

    /** Streams the LLM response token-by-token (reactive). */
    Flux<String> stream(String systemPromptTemplate, String context, String question);
}
