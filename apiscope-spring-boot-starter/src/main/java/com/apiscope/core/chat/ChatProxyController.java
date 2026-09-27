package com.apiscope.core.chat;

import com.apiscope.core.config.AgenticDocsProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/apiscope/api/chat")
public class ChatProxyController {

    private final RestClient restClient;
    private final AgenticDocsProperties props;

    public ChatProxyController(AgenticDocsProperties props) {
        this.props      = props;
        this.restClient = RestClient.create();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "question is required"));
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(props.llmServiceUrl() + "/chat")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(Map.of("question", question))
                    .retrieve()
                    .body(Map.class);
            return ResponseEntity.ok(response != null ? response : Map.of("answer", "No response from LLM service."));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("answer",
                    "AI service unavailable. Make sure the Python RAG service is running on " + props.llmServiceUrl()));
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of("message", "Use POST /apiscope/api/chat with body {\"question\": \"...\"}"));
    }
}
