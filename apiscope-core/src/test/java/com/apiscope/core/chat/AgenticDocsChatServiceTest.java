package com.apiscope.core.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticDocsChatServiceTest {

    @Test
    @DisplayName("answer() returns UNAVAILABLE message when service is down")
    void answer_returnsUnavailable_whenServiceDown() {
        // Service built with a URL that won't connect
        var props = new com.apiscope.core.config.AgenticDocsProperties(
                true, "http://localhost:19999",
                new com.apiscope.core.config.AgenticDocsProperties.RateLimit(false, 20),
                new com.apiscope.core.config.AgenticDocsProperties.Cors(java.util.List.of())
        );
        var service = new AgenticDocsChatService(props);
        var response = service.answer(new com.apiscope.core.model.ChatRequest("How do I list users?"));
        assertThat(response.answer()).contains("uvicorn");
    }

    @Test
    @DisplayName("stream() returns UNAVAILABLE iterator when service is down")
    void stream_returnsUnavailable_whenServiceDown() {
        var props = new com.apiscope.core.config.AgenticDocsProperties(
                true, "http://localhost:19999",
                new com.apiscope.core.config.AgenticDocsProperties.RateLimit(false, 20),
                new com.apiscope.core.config.AgenticDocsProperties.Cors(java.util.List.of())
        );
        var service = new AgenticDocsChatService(props);
        var it = service.stream(new com.apiscope.core.model.ChatRequest("test"));
        assertThat(it.hasNext()).isTrue();
        assertThat(it.next()).contains("uvicorn");
    }
}
