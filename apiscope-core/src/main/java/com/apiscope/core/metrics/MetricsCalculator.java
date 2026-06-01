package com.apiscope.core.metrics;

import org.springframework.boot.actuate.metrics.MetricsEndpoint;

import java.util.List;
import java.util.Map;

/**
 * Calculates avg response time and success rate from Micrometer metrics.
 */
class MetricsCalculator {

    private final MetricsEndpoint metricsEndpoint;

    MetricsCalculator(MetricsEndpoint metricsEndpoint) {
        this.metricsEndpoint = metricsEndpoint;
    }

    Map<String, Object> calculate(String uri, String method) {
        MetricsEndpoint.MetricDescriptor all = metricsEndpoint.metric(
                "http.server.requests", List.of("uri:" + uri, "method:" + method));

        if (all == null) return unavailable();

        double totalCount = sampleValue(all, "COUNT");
        if (totalCount == 0) return unavailable();

        double totalDuration = sampleValue(all, "TOTAL_TIME");
        double successCount  = countSuccessRequests(uri, method);

        return Map.of(
                "avgResponseMs", Math.round((totalDuration / totalCount) * 1000 * 10.0) / 10.0,
                "successRate",   Math.round((successCount / totalCount) * 100 * 10.0) / 10.0,
                "totalRequests", (long) totalCount,
                "available",     true
        );
    }

    private double countSuccessRequests(String uri, String method) {
        double count = 0;
        for (String status : List.of("200", "201", "202", "204")) {
            MetricsEndpoint.MetricDescriptor r = metricsEndpoint.metric(
                    "http.server.requests", List.of("uri:" + uri, "method:" + method, "status:" + status));
            if (r != null) count += sampleValue(r, "COUNT");
        }
        return count;
    }

    private double sampleValue(MetricsEndpoint.MetricDescriptor metric, String statistic) {
        if (metric == null || metric.getMeasurements() == null) return 0;
        return metric.getMeasurements().stream()
                .filter(s -> statistic.equalsIgnoreCase(s.getStatistic().name()))
                .mapToDouble(MetricsEndpoint.Sample::getValue)
                .findFirst().orElse(0);
    }

    static Map<String, Object> unavailable() {
        return Map.of("available", false);
    }
}
