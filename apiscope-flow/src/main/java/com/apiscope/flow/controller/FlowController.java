package com.apiscope.flow.controller;

import com.apiscope.flow.executor.FlowExecutorService;
import com.apiscope.flow.model.FlowRequest;
import com.apiscope.flow.spi.TraceEmitterProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

/**
 * Exposes the Flow Tracer API used by the React frontend.
 *
 * <h2>Endpoints</h2>
 * <pre>
 * POST /apiscope/api/flow/execute
 *   Body:     FlowRequest  { httpMethod, path, pathParams, body }
 *   Response: { "traceId": "uuid" }   — returned immediately (async execution)
 *
 * GET /apiscope/api/flow/trace/{traceId}
 *   Produces: text/event-stream
 *   Events:
 *     event: step   data: { TraceEvent JSON }    — one per intercepted method call
 *     event: done   data: { FlowDoneEvent JSON } — final HTTP result
 *     event: error  data: { "message": "..." }   — network / fatal error
 * </pre>
 */
@RestController
@RequestMapping("/apiscope/api/flow")
public class FlowController {

    private final TraceEmitterProvider  emitterProvider;
    private final FlowExecutorService executor;

    public FlowController(TraceEmitterProvider emitterProvider, FlowExecutorService executor) {
        this.emitterProvider = emitterProvider;
        this.executor = executor;
    }

    /**
     * Starts an async traced execution.
     * Returns the {@code traceId} immediately so the frontend can open the SSE stream
     * before (or concurrently with) the execution starting.
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, String>> execute(@RequestBody FlowRequest request) {
        String traceId = UUID.randomUUID().toString();
        emitterProvider.register(traceId);
        executor.executeAsync(traceId, request);
        return ResponseEntity.accepted().body(Map.of("traceId", traceId));
    }

    @GetMapping(value = "/trace/{traceId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter trace(@PathVariable String traceId) {
        return emitterProvider.attach(traceId);
    }
}
