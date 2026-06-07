package com.apiscope.core.chat;

import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

@Service
public class AgenticDocsChatService {

    private static final Logger log = LoggerFactory.getLogger(AgenticDocsChatService.class);
    private static final String UNAVAILABLE = "AI chat is unavailable — start the Python LLM service: `uvicorn main:app --port 8000`";

    private final RestClient http;

    public AgenticDocsChatService(com.apiscope.core.config.AgenticDocsProperties props) {
        this.http = RestClient.builder().baseUrl(props.llmServiceUrl()).build();
    }

    public ChatResponse answer(ChatRequest request) {
        try {
            Map<String, String> body = http.post()
                    .uri("/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("question", request.question()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});

            String answer = body != null ? body.getOrDefault("answer", "") : "";
            return new ChatResponse(answer.isBlank() ? "No relevant endpoint found." : answer);
        } catch (ResourceAccessException ex) {
            log.warn("[APIScope] LLM service unreachable: {}", ex.getMessage());
            return new ChatResponse(UNAVAILABLE);
        }
    }

    public Iterator<String> stream(ChatRequest request) {
        try {
            String body = http.post()
                    .uri("/chat/stream")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("question", request.question()))
                    .retrieve()
                    .body(String.class);

            if (body == null) return Collections.emptyIterator();

            return body.lines()
                    .filter(line -> line.startsWith("data: "))
                    .map(line -> line.substring(6))
                    .filter(token -> !token.equals("[DONE]"))
                    .iterator();
        } catch (ResourceAccessException ex) {
            log.warn("[APIScope] LLM stream unreachable: {}", ex.getMessage());
            return Collections.singletonList(UNAVAILABLE).iterator();
        }
    }
}
