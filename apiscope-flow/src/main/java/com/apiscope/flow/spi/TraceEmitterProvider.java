package com.apiscope.flow.spi;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Abstraction for managing the SSE emitter lifecycle.
 *
 * <p>{@code FlowController} depends only on this interface so it is decoupled
 * from the concrete {@code FlowSseRegistry} implementation (DIP).
 */
public interface TraceEmitterProvider {

    /** Reserve a slot for the given traceId before async execution starts. */
    void register(String traceId);

    /**
     * Attach an SSE emitter to the trace slot and replay any buffered events.
     * Returns the emitter the controller should hand back to the browser.
     */
    SseEmitter attach(String traceId);
}
