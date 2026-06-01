package com.apiscope.core.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Proxies Micrometer's {@code http.server.requests} metric for a specific endpoint to the UI.
 * Only registered when {@code spring-boot-starter-actuator} is on the classpath.
 *
 * GET /apiscope/api/endpoint-metrics?uri=/api/v1/users&amp;method=GET
 */
@RestController
@RequestMapping("/apiscope/api/endpoint-metrics")
@ConditionalOnClass(MetricsEndpoint.class)
public class EndpointMetricsController {

    private static final Logger log = LoggerFactory.getLogger(EndpointMetricsController.class);

    private final MetricsCalculator calculator;

    public EndpointMetricsController(MetricsEndpoint metricsEndpoint) {
        this.calculator = new MetricsCalculator(metricsEndpoint);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMetrics(
            @RequestParam("uri") String uri,
            @RequestParam("method") String method) {
        try {
            return ResponseEntity.ok(calculator.calculate(uri, method));
        } catch (Exception ex) {
            log.debug("[APIScope] Metrics not available for {} {}: {}", method, uri, ex.getMessage());
            return ResponseEntity.ok(MetricsCalculator.unavailable());
        }
    }
}
