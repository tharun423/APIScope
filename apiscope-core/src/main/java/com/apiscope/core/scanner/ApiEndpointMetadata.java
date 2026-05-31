package com.apiscope.core.scanner;

import java.util.List;

/**
 * Immutable DTO representing a single REST endpoint discovered in the host application.
 * The {@link #toLlmReadableText()} method produces the text that gets embedded into the vector store.
 */
public record ApiEndpointMetadata(
        String path,
        String httpMethod,
        String controllerName,
        String methodName,
        String description,
        List<String> pathParams,
        List<String> requiredQueryParams,
        List<String> optionalQueryParams,
        String requestBodyType,
        String responseType
) {
    /**
     * Produces a structured plain-text representation suitable for LLM context injection.
     */
    public String toLlmReadableText() {
        return """
                Endpoint         : [%s] %s
                Controller       : %s
                Method           : %s
                Path Params      : %s
                Required Params  : %s
                Optional Params  : %s
                Request Body     : %s
                Response Type    : %s
                Summary          : %s
                """.formatted(
                httpMethod, path, controllerName, methodName,
                pathParams == null || pathParams.isEmpty() ? "none" : String.join(", ", pathParams),
                requiredQueryParams == null || requiredQueryParams.isEmpty() ? "none" : String.join(", ", requiredQueryParams),
                optionalQueryParams == null || optionalQueryParams.isEmpty() ? "none" : String.join(", ", optionalQueryParams),
                requestBodyType != null ? requestBodyType : "none",
                responseType != null ? responseType : "void",
                description);
    }
}
