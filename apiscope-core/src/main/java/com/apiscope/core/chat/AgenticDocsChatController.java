package com.apiscope.core.chat;

import com.apiscope.core.ingestor.ApiDocumentIngestor;
import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import com.apiscope.core.scanner.EndpointRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Iterator;

@RestController
@RequestMapping("/apiscope/api")
public class AgenticDocsChatController {

    private static final Logger log = LoggerFactory.getLogger(AgenticDocsChatController.class);

    private final EndpointRepository endpoints;
    private final AgenticDocsChatService chatService;
    private final ApiDocumentIngestor ingestor;

    public AgenticDocsChatController(EndpointRepository endpoints,
                                     AgenticDocsChatService chatService,
                                     ApiDocumentIngestor ingestor) {
        this.endpoints   = endpoints;
        this.chatService = chatService;
        this.ingestor    = ingestor;
    }

    @GetMapping("/endpoints")
    public ResponseEntity<?> listEndpoints() {
        return ResponseEntity.ok(endpoints.getScannedEndpoints());
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Validated @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.answer(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Validated @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        Thread.ofVirtual().start(() -> {
            try {
                Iterator<String> tokens = chatService.stream(request);
                while (tokens.hasNext()) {
                    emitter.send(SseEmitter.event().name("token").data(tokens.next()));
                }
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();
            } catch (Exception ex) {
                log.error("[APIScope] SSE error: {}", ex.getMessage());
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @PostMapping("/admin/reindex")
    public ResponseEntity<Void> reindex() {
        ingestor.reindex(endpoints.getScannedEndpoints());
        return ResponseEntity.accepted().build();
    }
}
