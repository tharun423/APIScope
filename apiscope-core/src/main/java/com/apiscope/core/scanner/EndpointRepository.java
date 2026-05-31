package com.apiscope.core.scanner;

import java.util.List;

/**
 * Contract for accessing discovered REST endpoints.
 * Implemented by {@link ApiMetadataScanner}. Provide a {@code @Primary @Bean EndpointRepository}
 * to supply endpoints from a custom source (YAML, OpenAPI spec, remote registry).
 */
@FunctionalInterface
public interface EndpointRepository {

    /** Returns an immutable, never-null snapshot of all discovered REST endpoints. */
    List<ApiEndpointMetadata> getScannedEndpoints();
}
