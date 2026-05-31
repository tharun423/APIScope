package com.apiscope.flow.registry;

import com.apiscope.flow.model.FlowDoneEvent;
import com.apiscope.flow.model.TraceEvent;
import com.apiscope.flow.spi.TraceEmitterProvider;
import com.apiscope.flow.spi.TraceEventSink;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry that maps each {@code traceId} to its {@link SseEmitter} and
 * a buffer of events produced before the frontend SSE connection was established.
 *
 * <p>Flow:
 * <ol>
 *   <li>{@link #register} — called by {@code FlowController} before async execution starts.</li>
 *   <li>{@link #attach}   — called when {@code GET /trace/{id}} opens the SSE stream;
 *                           replays buffered events immediately so the frontend never misses steps.</li>
 *   <li>{@link #pushStep} — called by {@code FlowAspect} for each intercepted method call.</li>
 *   <li>{@link #pushDone} — called by {@code FlowExecutorService} when the HTTP call completes.</li>
 *   <li>{@link #pushError}— called by {@code FlowExecutorService} on a network / fatal error.</li>
 * </ol>
 */
@Component
public class FlowSseRegistry implements TraceEventSink, TraceEmitterProvider {

    private static final Logger log = LoggerFactory.getLogger(FlowSseRegistry.class);

    /** SSE timeout — 60 seconds is generous for any real API chain. */
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final ObjectMapper objectMapper;

    /** Holds per-trace state. */
    private final Map<String, TraceEntry> registry = new ConcurrentHashMap<>();

    public FlowSseRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Reserve a slot for a new trace. Must be called before
     * {@link #pushStep}/{@link #pushDone} to avoid lost events.
     */
    public void register(String traceId) {
        registry.put(traceId, new TraceEntry());
    }

    /**
     * Attach an SSE emitter to an existing trace slot and replay any buffered events.
     * Returns the emitter that the controller should return to the browser.
     */
    public SseEmitter attach(String traceId) {
        TraceEntry entry = registry.get(traceId);
        if (entry == null) {
            // Unknown trace — return a completed emitter with an error event
            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
            sendEvent(emitter, "error", Map.of("message", "Unknown traceId: " + traceId));
            emitter.complete();
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Runnable cleanup = () -> {
            entry.emitter = null;
            registry.remove(traceId);
        };
        emitter.onTimeout(cleanup);
        emitter.onCompletion(cleanup);

        // Replay buffered events so the frontend never misses a step
        for (BufferedEvent buffered : entry.buffer) {
            sendEvent(emitter, buffered.eventName(), buffered.payload());
        }

        // Set live emitter AFTER replay so pushStep/pushDone can emit going forward
        entry.emitter = emitter;
        return emitter;
    }

    /**
     * Push a {@link TraceEvent} (one intercepted method call) to the SSE stream.
     * Buffers the event if the SSE connection has not been established yet.
     */
    public void pushStep(String traceId, TraceEvent event) {
        push(traceId, "step", event);
    }

    /**
     * Push the final {@link FlowDoneEvent} and complete the SSE stream.
     */
    public void pushDone(String traceId, FlowDoneEvent event) {
        push(traceId, "done", event);
        complete(traceId);
    }

    /**
     * Push an error event and complete the SSE stream.
     */
    public void pushError(String traceId, String message) {
        push(traceId, "error", Map.of("message", message));
        complete(traceId);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void push(String traceId, String eventName, Object payload) {
        TraceEntry entry = registry.get(traceId);
        if (entry == null) return;

        entry.buffer.add(new BufferedEvent(eventName, payload));

        SseEmitter emitter = entry.emitter;
        if (emitter != null) {
            sendEvent(emitter, eventName, payload);
        }
    }

    private void complete(String traceId) {
        TraceEntry entry = registry.get(traceId);
        if (entry == null) return;
        SseEmitter emitter = entry.emitter;
        if (emitter != null) {
            try { emitter.complete(); } catch (Exception ignored) {}
        }
        registry.remove(traceId);
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (JsonProcessingException e) {
            log.warn("Flow SSE: failed to serialize payload for event '{}': {}", eventName, e.getMessage());
        } catch (IOException e) {
            log.debug("Flow SSE: client disconnected for event '{}': {}", eventName, e.getMessage());
        }
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private static final class TraceEntry {
        final List<BufferedEvent> buffer  = new CopyOnWriteArrayList<>();
        volatile SseEmitter       emitter = null;
    }

    private record BufferedEvent(String eventName, Object payload) {}
}
