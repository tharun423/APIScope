package com.apiscope.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "apiscope")
@Validated
public record AgenticDocsProperties(
        @DefaultValue("true")  boolean enabled,
        @DefaultValue("http://localhost:8000") String llmServiceUrl,
        @DefaultValue @Valid RateLimit rateLimit,
        @DefaultValue @Valid Cors cors
) {
    public record Cors(
            @DefaultValue("http://localhost:5173") List<String> allowedOrigins
    ) {}

    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("20") @Min(1) @Max(10000) int requestsPerMinute
    ) {}
}
