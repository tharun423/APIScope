package com.apiscope.core.ratelimit;

import com.apiscope.core.config.AgenticDocsProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/**
 * Per-IP rate limiter using Bucket4j's in-memory token-bucket algorithm.
 * Each IP gets its own bucket (refilled every 60 s). Buckets are stored in a
 * size-bounded LRU map (max 10,000 entries) to prevent unbounded memory growth.
 * For clustered deployments swap with a Bucket4j-Redis proxy.
 *
 * Configured via {@code apiscope.rate-limit.*} in application.properties.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final int MAX_BUCKETS = 10_000;

    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > MAX_BUCKETS;
                }
            });
    private final AgenticDocsProperties properties;

    public RateLimiterService(AgenticDocsProperties properties) {
        this.properties = properties;
    }

    /** Returns {@code true} if the request is allowed, {@code false} if rate limit is exceeded. */
    public boolean tryConsume(String clientIp) {
        AgenticDocsProperties.RateLimit config = properties.rateLimit();
        if (!config.enabled()) return true;

        Bucket bucket = buckets.computeIfAbsent(clientIp, ip -> buildBucket(config));
        boolean allowed = bucket.tryConsume(1);
        if (!allowed) {
            log.warn("[APIScope] Rate limit exceeded for IP: {} ({} req/min)",
                    clientIp, config.requestsPerMinute());
        }
        return allowed;
    }

    private Bucket buildBucket(AgenticDocsProperties.RateLimit config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.requestsPerMinute())
                .refillGreedy(config.requestsPerMinute(), Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
