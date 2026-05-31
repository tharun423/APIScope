package com.apiscope.core.model;

/**
 * Response body returned by the {@code POST /apiscope/api/chat} endpoint.
 *
 * @param answer the LLM-generated answer (or a friendly fallback message)
 */
public record ChatResponse(String answer) {}
