package com.apiscope.flow.controller;

import com.apiscope.flow.executor.FlowExecutorService;
import com.apiscope.flow.model.FlowRequest;
import com.apiscope.flow.registry.FlowSseRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/apiscope/api/flow")
public class FlowController {

    private final FlowSseRegistry registry;
    private final FlowExecutorService executor;

    public FlowController(FlowSseRegistry registry, FlowExecutorService executor) {
        this.registry = registry;
        this.executor = executor;
    }

    // Returns traceId immediately so the frontend can open SSE before execution starts
    @PostMapping("/execute")
    public ResponseEntity<Map<String, String>> execute(@RequestBody FlowRequest request) {
        String traceId = UUID.randomUUID().toString();
        registry.register(traceId);
        executor.executeAsync(traceId, request);
        return ResponseEntity.accepted().body(Map.of("traceId", traceId));
    }

    @GetMapping(value = "/trace/{traceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trace(@PathVariable String traceId) {
        return registry.attach(traceId);
    }
}
