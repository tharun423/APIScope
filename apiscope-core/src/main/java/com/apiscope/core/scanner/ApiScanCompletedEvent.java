package com.apiscope.core.scanner;

import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Domain event fired by {@link ApiMetadataScanner} when endpoint discovery is complete.
 * Downstream listeners (e.g. {@link com.apiscope.core.ingestor.ApiDocumentIngestor})
 * react to this instead of the generic Spring {@code ContextRefreshedEvent},
 * keeping the trigger explicit and removing {@code @Order} coupling.
 */
public class ApiScanCompletedEvent extends ApplicationEvent {

    private final List<ApiEndpointMetadata> endpoints;

    public ApiScanCompletedEvent(Object source, List<ApiEndpointMetadata> endpoints) {
        super(source);
        this.endpoints = List.copyOf(endpoints);
    }

    /**
     * Returns the endpoints discovered during the scan.
     * The list is immutable; endpoints are already filtered and validated.
     */
    public List<ApiEndpointMetadata> endpoints() {
        return endpoints;
    }
}
