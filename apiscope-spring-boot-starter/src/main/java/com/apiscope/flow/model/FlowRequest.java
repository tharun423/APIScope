package com.apiscope.flow.model;

import java.util.Map;

public record FlowRequest(
        String              httpMethod,
        String              path,
        Map<String, String> pathParams,
        Map<String, String> queryParams,
        String              body,
        String              authorizationHeader
) {}
