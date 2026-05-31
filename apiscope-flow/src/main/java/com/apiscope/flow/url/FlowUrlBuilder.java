package com.apiscope.flow.url;

import com.apiscope.flow.model.FlowRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Builds the full target URL from a {@link FlowRequest}, substituting any
 * path-parameter tokens and appending query parameters.
 * Extracted from {@code FlowExecutorService} (SRP).
 */
@Component
public class FlowUrlBuilder {

    private final int serverPort;

    public FlowUrlBuilder(@Value("${server.port:8080}") int serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * Resolve all {@code {param}} tokens in the request path, prepend the
     * local base URL, and append any query parameters.
     *
     * @param request the incoming flow request
     * @return a fully-qualified URL such as {@code http://localhost:8080/api/v1/analytics/revenue?fromDate=2026-01-01&toDate=2026-04-30}
     */
    public String build(FlowRequest request) {
        String path = request.path();

        if (request.pathParams() != null) {
            for (Map.Entry<String, String> entry : request.pathParams().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = (entry.getValue() != null && !entry.getValue().isBlank())
                        ? entry.getValue()
                        : "_";
                path = path.replace(placeholder, value);
            }
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromHttpUrl("http://localhost:" + serverPort + path);

        if (request.queryParams() != null) {
            request.queryParams().forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    uriBuilder.queryParam(key, value);
                }
            });
        }

        return uriBuilder.toUriString();
    }
}
