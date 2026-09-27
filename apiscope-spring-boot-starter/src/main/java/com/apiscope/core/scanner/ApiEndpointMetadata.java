package com.apiscope.core.scanner;

import java.util.List;

public record ApiEndpointMetadata(
        String path,
        String httpMethod,
        String controllerName,
        String methodName,
        String businessLogic,
        List<String> pathParams,
        List<String> requiredQueryParams,
        List<String> optionalQueryParams,
        String requestBodyType,
        String responseType
) {}
