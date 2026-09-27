package com.apiscope.core.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "apiscope")
@Validated
public record AgenticDocsProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean rateLimitEnabled,
        @DefaultValue("20") @Min(1) @Max(10000) int requestsPerMinute,
        @DefaultValue("http://localhost:5173") String corsAllowedOrigin,
        @DefaultValue("http://localhost:8000") String llmServiceUrl
) {}
