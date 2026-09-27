package com.apiscope.flow.url;

import com.apiscope.flow.model.FlowRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class FlowUrlBuilder {

    private final int serverPort;

    public FlowUrlBuilder(@Value("${server.port:8080}") int serverPort) {
        this.serverPort = serverPort;
    }

    public String build(FlowRequest request) {
        // Replace {param} tokens with their values
        String path = request.path();
        if (request.pathParams() != null) {
            for (var entry : request.pathParams().entrySet()) {
                String value = (entry.getValue() != null && !entry.getValue().isBlank())
                        ? entry.getValue() : "_";
                path = path.replace("{" + entry.getKey() + "}", value);
            }
        }

        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme("http").host("localhost").port(serverPort).path(path);

        if (request.queryParams() != null) {
            request.queryParams().forEach((key, value) -> {
                if (value != null && !value.isBlank()) builder.queryParam(key, value);
            });
        }

        return builder.toUriString();
    }
}
