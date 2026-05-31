package com.apiscope.core.chat;

import com.apiscope.core.ingestor.ApiDocumentIngestor;
import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.EndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/** Exposes Agentic Docs HTTP endpoints. Rate limiting is handled by {@link com.apiscope.core.ratelimit.RateLimitInterceptor}. */
@RestController
@RequestMapping("/apiscope/api")
public class AgenticDocsChatController {

    private static final Logger log = LoggerFactory.getLogger(AgenticDocsChatController.class);

    private final EndpointRepository endpointRepository;
    private final ChatPort chatPort;
    private final ApiDocumentIngestor ingestor;

    public AgenticDocsChatController(EndpointRepository endpointRepository,
                                     ChatPort chatPort,
                                     ApiDocumentIngestor ingestor) {
        this.endpointRepository = endpointRepository;
        this.chatPort           = chatPort;
        this.ingestor           = ingestor;
    }

    @GetMapping("/endpoints")
    public ResponseEntity<List<ApiEndpointMetadata>> listEndpoints() {
        return ResponseEntity.ok(endpointRepository.getScannedEndpoints());
    }

    /** Forces a full re-scan and re-ingest of all endpoints into the vector store. */
    @PostMapping("/admin/reindex")
    public ResponseEntity<Void> reindex() {
        log.info("[APIScope] Manual reindex triggered.");
        ingestor.reindex(endpointRepository.getScannedEndpoints());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Validated @RequestBody ChatRequest request) {
        log.debug("[APIScope] Received chat request.");
        return ResponseEntity.ok(chatPort.answer(request));
    }

    /** Streaming chat via SSE. Events: token, done, error. */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> chatStream(
            @Validated @RequestBody ChatRequest request) {

        Flux<ServerSentEvent<String>> sseFlux = chatPort.streamAnswer(request)
                .map(token -> ServerSentEvent.<String>builder()
                        .event("token").data(token).build())
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done").data("[DONE]").build()))
                .onErrorResume(ex -> {
                    log.error("[APIScope] Streaming error: {}", ex.getMessage(), ex);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("Streaming failed: " + ex.getMessage())
                            .build());
                });

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(sseFlux);
    }
}
