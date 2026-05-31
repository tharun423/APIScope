package com.apiscope.core.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the {@code POST /apiscope/api/chat} endpoint.
 *
 * <p>This is a Java {@code record} — an immutable data carrier.
 * The field annotations enforce rules <em>before</em> the request reaches the controller:
 * <ul>
 *   <li>{@code @NotBlank} — the question must not be null, empty, or whitespace-only.</li>
 *   <li>{@code @Size(max = 2000)} — prevents excessively large LLM prompts that could
 *       spike token costs or cause timeout errors.</li>
 * </ul>
 *
 * <p>Example JSON body:
 * <pre>{@code {"question": "How do I create a new payment?"}}</pre>
 *
 * @param question the natural-language question asked by the developer
 */
public record ChatRequest(
        @NotBlank(message = "Question must not be blank")
        @Size(max = 2000, message = "Question must not exceed 2000 characters")
        String question
) {}
