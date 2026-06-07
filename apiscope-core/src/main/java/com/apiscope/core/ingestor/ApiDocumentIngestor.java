package com.apiscope.core.ingestor;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.ApiScanCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ApiDocumentIngestor {

    private static final Logger log = LoggerFactory.getLogger(ApiDocumentIngestor.class);

    private final RestClient http;
    private final AtomicBoolean ingested = new AtomicBoolean(false);

    public ApiDocumentIngestor(AgenticDocsProperties props) {
        this.http = RestClient.builder().baseUrl(props.llmServiceUrl()).build();
    }

    @EventListener
    public void onScanCompleted(ApiScanCompletedEvent event) {
        if (!ingested.compareAndSet(false, true)) return;
        ingest(event.endpoints());
    }

    public void reindex(List<ApiEndpointMetadata> endpoints) {
        ingested.set(false);
        ingest(endpoints);
    }

    private void ingest(List<ApiEndpointMetadata> endpoints) {
        if (endpoints.isEmpty()) {
            log.warn("[APIScope] No endpoints to ingest.");
            return;
        }

        List<Map<String, Object>> payload = endpoints.stream().map(e -> Map.<String, Object>of(
                "path",                e.path(),
                "httpMethod",          e.httpMethod(),
                "controllerName",      e.controllerName(),
                "methodName",          e.methodName(),
                "description",         e.description() != null ? e.description() : "",
                "pathParams",          e.pathParams() != null ? e.pathParams() : List.of(),
                "requiredQueryParams",  e.requiredQueryParams() != null ? e.requiredQueryParams() : List.of(),
                "optionalQueryParams",  e.optionalQueryParams() != null ? e.optionalQueryParams() : List.of(),
                "requestBodyType",     e.requestBodyType() != null ? e.requestBodyType() : "",
                "responseType",        e.responseType() != null ? e.responseType() : ""
        )).toList();

        try {
            http.post()
                    .uri("/ingest")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("endpoints", payload))
                    .retrieve()
                    .toBodilessEntity();
            log.info("[APIScope] Ingested {} endpoints into LLM service.", payload.size());
        } catch (ResourceAccessException ex) {
            log.warn("[APIScope] LLM service unreachable during ingest — AI chat will not work. Start: `uvicorn main:app --port 8000`");
        }
    }
}
