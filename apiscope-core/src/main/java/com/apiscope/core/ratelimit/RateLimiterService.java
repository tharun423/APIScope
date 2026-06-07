package com.apiscope.core.ratelimit;

import com.apiscope.core.config.AgenticDocsProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    // LRU map capped at 10k IPs to prevent unbounded memory growth
    private final Map<String, Bucket> buckets = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                    return size() > 10_000;
                }
            });

    private final AgenticDocsProperties props;

    public RateLimiterService(AgenticDocsProperties props) {
        this.props = props;
    }

    public boolean tryConsume(String clientIp) {
        if (!props.rateLimit().enabled()) return true;
        boolean allowed = buckets.computeIfAbsent(clientIp, ip -> newBucket()).tryConsume(1);
        if (!allowed) log.warn("[APIScope] Rate limit exceeded for IP: {}", clientIp);
        return allowed;
    }

    private Bucket newBucket() {
        int rpm = props.rateLimit().requestsPerMinute();
        return Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(rpm).refillGreedy(rpm, Duration.ofMinutes(1)).build())
                .build();
    }
}
