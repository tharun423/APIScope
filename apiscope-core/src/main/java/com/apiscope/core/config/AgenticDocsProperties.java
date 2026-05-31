package com.apiscope.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Configuration properties for Agentic Docs (prefix: {@code agentic.docs}).
 * Validated at startup — invalid values cause a clear failure instead of a silent runtime bug.
 *
 * <pre>
 * apiscope.enabled=true
 * apiscope.top-k=5                          # 1-50
 * apiscope.system-prompt=...{context}...    # optional; must contain {context}
 * apiscope.vector-store-path=./apiscope-vector-store.json
 * apiscope.rate-limit.enabled=true
 * apiscope.rate-limit.requests-per-minute=20
 * apiscope.cors.allowed-origins=http://localhost:5173
 * </pre>
 */
@ConfigurationProperties(prefix = "apiscope")
@Validated
public record AgenticDocsProperties(
        @DefaultValue("true")                              boolean   enabled,
        @DefaultValue("5")   @Min(1) @Max(50)             int       topK,
        String                                             systemPrompt,
        @DefaultValue("./apiscope-vector-store.json")
        @NotBlank                                          String    vectorStorePath,
        @DefaultValue @Valid                               RateLimit rateLimit,
        @DefaultValue @Valid                               Cors      cors
) {

    /** CORS settings — maps to {@code apiscope.cors.*}. */
    public record Cors(
            @DefaultValue("http://localhost:5173") List<String> allowedOrigins
    ) {}

    /** Per-IP rate limit settings — maps to {@code apiscope.rate-limit.*}. */
    public record RateLimit(
            @DefaultValue("true")   boolean enabled,
            @DefaultValue("20")     @Min(1) @Max(10000) int requestsPerMinute
    ) {}
}
