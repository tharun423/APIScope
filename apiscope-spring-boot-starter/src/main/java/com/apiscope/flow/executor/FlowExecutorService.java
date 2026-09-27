package com.apiscope.flow.executor;

import com.apiscope.flow.aspect.FlowAspect;
import com.apiscope.flow.model.FlowDoneEvent;
import com.apiscope.flow.model.FlowRequest;
import com.apiscope.flow.registry.FlowSseRegistry;
import com.apiscope.flow.url.FlowUrlBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FlowExecutorService {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutorService.class);

    private final FlowSseRegistry registry;
    private final FlowUrlBuilder urlBuilder;
    private final RestClient restClient;
    private final FlowAspect flowAspect;

    public FlowExecutorService(FlowSseRegistry registry, FlowUrlBuilder urlBuilder,
                               @Qualifier("flowRestClient") RestClient restClient, FlowAspect flowAspect) {
        this.registry   = registry;
        this.urlBuilder = urlBuilder;
        this.restClient = restClient;
        this.flowAspect = flowAspect;
    }

    public void executeAsync(String traceId, FlowRequest request) {
        Thread.ofVirtual().name("flow-" + traceId).start(() -> execute(traceId, request));
    }

    private void execute(String traceId, FlowRequest request) {
        long start = System.currentTimeMillis();
        try {
            String url    = urlBuilder.build(request);
            String method = request.httpMethod().toUpperCase();
            log.debug("[Flow] {} {} trace={}", method, url, traceId);

            RestClient.RequestBodySpec spec = restClient
                    .method(HttpMethod.valueOf(method))
                    .uri(url)
                    .header(FlowAspect.TRACE_HEADER, traceId);

            if (request.authorizationHeader() != null && !request.authorizationHeader().isBlank()) {
                spec = spec.header("Authorization", request.authorizationHeader());
            }

            if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH")) {
                spec = spec.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                           .body(request.body() != null && !request.body().isBlank() ? request.body() : "{}");
            }

            ResponseEntity<String> response = spec
                    .retrieve()
                    .onStatus(status -> true, (req, res) -> {})
                    .toEntity(String.class);

            registry.pushDone(traceId, new FlowDoneEvent(
                    traceId,
                    response.getStatusCode().value(),
                    response.getBody() != null ? response.getBody() : "",
                    System.currentTimeMillis() - start,
                    response.getStatusCode().is2xxSuccessful(),
                    flowAspect.getAndClearStepCount(traceId)
            ));
        } catch (Exception ex) {
            log.warn("[Flow] Executor error trace={}: {}", traceId, ex.getMessage());
            flowAspect.clearStepCount(traceId);
            registry.pushError(traceId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
