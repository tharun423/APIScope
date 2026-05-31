package com.apiscope.flow.model;

import java.util.Map;

/**
 * Describes a single API request to execute and trace.
 * Sent by the frontend as the body of {@code POST /apiscope/api/flow/execute}.
 *
 * @param httpMethod           HTTP method (GET, POST, PUT, PATCH, DELETE).
 * @param path                 URL path, may contain {@code {param}} tokens.
 * @param pathParams           Values to substitute into {@code {param}} tokens (may be null/empty).
 * @param queryParams          Query string key-value pairs appended to the URL (may be null/empty).
 * @param body                 JSON request body (for POST / PUT / PATCH); may be null.
 * @param authorizationHeader  Optional Bearer token forwarded as {@code Authorization} header.
 */
public record FlowRequest(
        String              httpMethod,
        String              path,
        Map<String, String> pathParams,
        Map<String, String> queryParams,
        String              body,
        String              authorizationHeader
) {}
