package com.apiscope.core.chat;

import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/** Handles AI chat endpoints only. */
@RestController
@RequestMapping("/apiscope/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatPort chatPort;

    public ChatController(ChatPort chatPort) {
        this.chatPort = chatPort;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Validated @RequestBody ChatRequest request) {
        log.debug("[APIScope] Received chat request.");
        return ResponseEntity.ok(chatPort.answer(request));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> chatStream(
            @Validated @RequestBody ChatRequest request) {

        Flux<ServerSentEvent<String>> sseFlux = chatPort.streamAnswer(request)
                .map(token -> ServerSentEvent.<String>builder().event("token").data(token).build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder().event("done").data("[DONE]").build()))
                .onErrorResume(ex -> {
                    log.error("[APIScope] Streaming error: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error").data("Streaming failed: " + ex.getMessage()).build());
                });

        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(sseFlux);
    }
}
