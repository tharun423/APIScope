package com.apiscope.flow.executor;

import com.apiscope.flow.aspect.FlowAspect;
import com.apiscope.flow.model.FlowDoneEvent;
import com.apiscope.flow.model.FlowRequest;
import com.apiscope.flow.spi.TraceEventSink;
import com.apiscope.flow.url.FlowUrlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * Executes a traced API request on a virtual thread so the caller returns immediately.
 *
 * Flow:
 *  1. Build the full URL from the FlowRequest.
 *  2. Fire the HTTP call with the X-Flow-Trace-Id header so FlowAspect can correlate calls.
 *  3. Push a FlowDoneEvent when the call completes, or an error event on failure.
 */
@Service
public class FlowExecutorService {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutorService.class);
    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final TraceEventSink sink;
    private final FlowUrlBuilder urlBuilder;
    private final RestClient restClient;
    private final FlowAspect flowAspect;

    public FlowExecutorService(TraceEventSink sink, FlowUrlBuilder urlBuilder,
                                RestClient restClient, FlowAspect flowAspect) {
        this.sink       = sink;
        this.urlBuilder = urlBuilder;
        this.restClient = restClient;
        this.flowAspect = flowAspect;
    }

    /** Starts execution on a virtual thread. Returns immediately. */
    public void executeAsync(String traceId, FlowRequest request) {
        Thread.ofVirtual()
              .name("flow-tracer-" + traceId)
              .start(() -> execute(traceId, request));
    }

    private void execute(String traceId, FlowRequest request) {
        long start = System.currentTimeMillis();
        try {
            String url = urlBuilder.build(request);
            log.debug("Flow [{}] → {} {}", traceId, request.httpMethod(), url);

            ResponseEntity<String> response = buildSpec(request, url, traceId)
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> {})
                    .toEntity(String.class);

            sink.pushDone(traceId, buildDoneEvent(traceId, response, System.currentTimeMillis() - start));

        } catch (Exception ex) {
            log.warn("Flow [{}] executor error: {}", traceId, ex.getMessage());
            sink.pushError(traceId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private RestClient.RequestBodySpec buildSpec(FlowRequest request, String url, String traceId) {
        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.httpMethod().toUpperCase()))
                .uri(url)
                .header(FlowAspect.TRACE_HEADER, traceId)
                .header("Content-Type", "application/json");

        if (request.authorizationHeader() != null && !request.authorizationHeader().isBlank()) {
            spec = spec.header("Authorization", request.authorizationHeader());
        }
        if (BODY_METHODS.contains(request.httpMethod().toUpperCase())) {
            String body = (request.body() != null && !request.body().isBlank()) ? request.body() : "{}";
            spec = spec.body(body);
        }
        return spec;
    }

    private FlowDoneEvent buildDoneEvent(String traceId, ResponseEntity<String> response, long totalMs) {
        return new FlowDoneEvent(
                traceId,
                response.getStatusCode().value(),
                response.getBody() != null ? response.getBody() : "",
                totalMs,
                response.getStatusCode().is2xxSuccessful(),
                flowAspect.getAndClearStepCount(traceId)
        );
    }
}
