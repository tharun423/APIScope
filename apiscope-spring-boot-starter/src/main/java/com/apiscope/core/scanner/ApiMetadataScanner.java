package com.apiscope.core.scanner;

import com.apiscope.core.config.AgenticDocsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ApiMetadataScanner implements EndpointRepository {

    private static final Logger log = LoggerFactory.getLogger(ApiMetadataScanner.class);

    private static final List<String> INTERNAL_PACKAGES = List.of(
            "com.apiscope.core", "com.apiscope.autoconfigure", "com.apiscope.flow"
    );

    private final RequestMappingHandlerMapping handlerMapping;
    private final EndpointMetadataExtractor extractor;
    private final AgenticDocsProperties props;
    private final RestClient restClient;
    private final AtomicBoolean scanned = new AtomicBoolean(false);
    private volatile List<ApiEndpointMetadata> scannedEndpoints = List.of();

    public ApiMetadataScanner(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            EndpointMetadataExtractor extractor,
            AgenticDocsProperties props,
            @Qualifier("flowRestClient") RestClient restClient) {
        this.handlerMapping = handlerMapping;
        this.extractor      = extractor;
        this.props          = props;
        this.restClient     = restClient;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onContextRefreshed() {
        if (!scanned.compareAndSet(false, true)) return;

        List<ApiEndpointMetadata> endpoints = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(e -> isUserController(e.getValue().getBeanType()))
                .map(e -> extractor.extract(e.getKey(), e.getValue()))
                .filter(e -> !"/unknown".equals(e.path()))
                .toList();

        this.scannedEndpoints = List.copyOf(endpoints);
        log.info("[APIScope] Scanned {} REST endpoints.", endpoints.size());
        pushToLlmService(endpoints);
    }

    private void pushToLlmService(List<ApiEndpointMetadata> endpoints) {
        try {
            restClient.post()
                    .uri(props.llmServiceUrl() + "/ingest")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(endpoints)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[APIScope] Pushed {} endpoints to LLM service.", endpoints.size());
        } catch (Exception ex) {
            log.warn("[APIScope] Could not push endpoints to LLM service: {}", ex.getMessage());
        }
    }

    @Override
    public List<ApiEndpointMetadata> getScannedEndpoints() {
        return scannedEndpoints;
    }

    private boolean isUserController(Class<?> beanType) {
        return beanType.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class)
                && INTERNAL_PACKAGES.stream().noneMatch(beanType.getName()::startsWith);
    }
}
