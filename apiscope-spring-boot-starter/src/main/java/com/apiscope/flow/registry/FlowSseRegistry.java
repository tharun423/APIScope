package com.apiscope.flow.registry;

import com.apiscope.flow.model.FlowDoneEvent;
import com.apiscope.flow.model.TraceEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class FlowSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(FlowSseRegistry.class);
    private static final long SSE_TIMEOUT_MS  = 60_000L;
    // Auto-evict stale entries after 2 minutes (client never connected)
    private static final long STALE_THRESHOLD_MS = 120_000L;

    private final ObjectMapper objectMapper;
    private final Map<String, TraceEntry> registry = new ConcurrentHashMap<>();

    public FlowSseRegistry(@Qualifier("flowObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(String traceId) {
        registry.put(traceId, new TraceEntry());
    }

    public SseEmitter attach(String traceId) {
        TraceEntry entry = registry.get(traceId);
        if (entry == null) {
            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
            sendEvent(emitter, "error", Map.of("message", "Unknown traceId: " + traceId));
            emitter.complete();
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onTimeout(() -> registry.remove(traceId));
        emitter.onCompletion(() -> registry.remove(traceId));

        // Replay buffered events (produced before SSE connection opened)
        for (BufferedEvent buffered : entry.buffer) {
            sendEvent(emitter, buffered.eventName(), buffered.payload());
        }
        entry.emitter = emitter;
        return emitter;
    }

    public void pushStep(String traceId, TraceEvent event) {
        push(traceId, "step", event);
    }

    public void pushDone(String traceId, FlowDoneEvent event) {
        push(traceId, "done", event);
        complete(traceId);
    }

    public void pushError(String traceId, String message) {
        push(traceId, "error", Map.of("message", message));
        complete(traceId);
    }

    @PostConstruct
    void startEviction() {
        Thread.ofVirtual().name("flow-evict").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000);
                    evictStale();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    // Evict trace entries that were registered but never had an SSE client attach
    void evictStale() {
        long now = System.currentTimeMillis();
        registry.entrySet().removeIf(e ->
                e.getValue().emitter == null &&
                (now - e.getValue().createdAt) > STALE_THRESHOLD_MS
        );
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void push(String traceId, String eventName, Object payload) {
        TraceEntry entry = registry.get(traceId);
        if (entry == null) return;
        entry.buffer.add(new BufferedEvent(eventName, payload));
        if (entry.emitter != null) {
            sendEvent(entry.emitter, eventName, payload);
        }
    }

    private void complete(String traceId) {
        TraceEntry entry = registry.remove(traceId);
        if (entry != null && entry.emitter != null) {
            try { entry.emitter.complete(); } catch (Exception ignored) {}
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(objectMapper.writeValueAsString(payload)));
        } catch (JsonProcessingException e) {
            log.warn("[Flow] Failed to serialize SSE event '{}': {}", eventName, e.getMessage());
        } catch (IOException e) {
            log.debug("[Flow] Client disconnected on event '{}'", eventName);
        }
    }

    private static final class TraceEntry {
        final long createdAt = System.currentTimeMillis();
        final List<BufferedEvent> buffer = new CopyOnWriteArrayList<>();
        volatile SseEmitter emitter = null;
    }

    private record BufferedEvent(String eventName, Object payload) {}
}
