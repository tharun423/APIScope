package com.apiscope.core.scanner;

import java.util.List;

@FunctionalInterface
public interface EndpointRepository {
    List<ApiEndpointMetadata> getScannedEndpoints();
}
