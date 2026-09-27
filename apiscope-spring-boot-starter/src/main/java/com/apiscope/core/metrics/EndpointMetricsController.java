package com.apiscope.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Proxies Micrometer's {@code http.server.requests} metric for a specific endpoint
 * to the Agentic Docs UI. Avoids exposing actuator publicly or hitting CORS issues.
 *
 * Only registered when {@code MetricsEndpoint} is on the classpath
 * (i.e. {@code spring-boot-starter-actuator} is a dependency of the host app).
 *
 * GET /apiscope/api/endpoint-metrics?uri=/api/v1/users&amp;method=GET
 *
 * Response:
 * {
 *   "avgResponseMs": 24.3,
 *   "successRate": 99.1,
 *   "totalRequests": 412,
 *   "available": true
 * }
 */
@RestController
@RequestMapping("/apiscope/api/endpoint-metrics")
@ConditionalOnClass(MetricsEndpoint.class)
public class EndpointMetricsController {

    private static final Logger log = LoggerFactory.getLogger(EndpointMetricsController.class);

    private final MetricsEndpoint metricsEndpoint;

    public EndpointMetricsController(MetricsEndpoint metricsEndpoint) {
        this.metricsEndpoint = metricsEndpoint;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam("uri") String uri,
            @RequestParam("method") String method) {
        try {
            // Total count for this uri+method across all status codes
            MetricsEndpoint.MetricDescriptor all = metricsEndpoint.metric(
                    "http.server.requests",
                    List.of("uri:" + uri, "method:" + method)
            );

            if (all == null) return ResponseEntity.ok(unavailable());

            double totalCount = sampleValue(all, "COUNT");
            if (totalCount == 0) return ResponseEntity.ok(unavailable());

            double totalDuration = sampleValue(all, "TOTAL_TIME"); // seconds

            // Success count: 2xx responses
            double successCount = 0;
            for (String status : List.of("200", "201", "202", "204")) {
                MetricsEndpoint.MetricDescriptor r = metricsEndpoint.metric(
                        "http.server.requests",
                        List.of("uri:" + uri, "method:" + method, "status:" + status)
                );
                if (r != null) successCount += sampleValue(r, "COUNT");
            }

            double avgResponseMs = (totalDuration / totalCount) * 1000;
            double successRate   = (successCount / totalCount) * 100;

            return ResponseEntity.ok(Map.of(
                    "avgResponseMs",  Math.round(avgResponseMs * 10.0) / 10.0,
                    "successRate",    Math.round(successRate   * 10.0) / 10.0,
                    "totalRequests",  (long) totalCount,
                    "available",      true
            ));

        } catch (Exception ex) {
            log.debug("[APIScope] Metrics not available for {} {}: {}", method, uri, ex.getMessage());
            return ResponseEntity.ok(unavailable());
        }
    }

    private static double sampleValue(MetricsEndpoint.MetricDescriptor metric, String statistic) {
        if (metric == null || metric.getMeasurements() == null) return 0;
        return metric.getMeasurements().stream()
                .filter(s -> statistic.equalsIgnoreCase(s.getStatistic().name()))
                .mapToDouble(MetricsEndpoint.Sample::getValue)
                .findFirst()
                .orElse(0);
    }

    private static Map<String, Object> unavailable() {
        return Map.of("available", false);
    }
}
