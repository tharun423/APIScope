package com.apiscope.core.ratelimit;

import com.apiscope.core.config.AgenticDocsProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    // One bucket per IP, auto-evicted after 1 hour of inactivity, max 10k IPs
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private final AgenticDocsProperties props;

    public RateLimiterService(AgenticDocsProperties props) {
        this.props = props;
    }

    public boolean tryConsume(String clientIp) {
        if (!props.rateLimitEnabled()) return true;
        boolean allowed = buckets.get(clientIp, ip -> newBucket()).tryConsume(1);
        if (!allowed) log.warn("[APIScope] Rate limit exceeded for IP: {}", clientIp);
        return allowed;
    }

    private Bucket newBucket() {
        int rpm = props.requestsPerMinute();
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rpm)
                        .refillGreedy(rpm, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
