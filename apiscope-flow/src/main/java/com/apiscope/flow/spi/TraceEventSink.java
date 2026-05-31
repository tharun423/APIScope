package com.apiscope.flow.spi;

import com.apiscope.flow.model.FlowDoneEvent;
import com.apiscope.flow.model.TraceEvent;

/**
 * Abstraction for publishing trace events.
 *
 * <p>Consumers ({@code FlowAspect}, {@code FlowExecutorService}) depend on this
 * interface rather than the concrete {@code FlowSseRegistry}. This satisfies the
 * Dependency Inversion Principle (D) and the Interface Segregation Principle (I).
 */
public interface TraceEventSink {

    /** Publish a single intercepted-method snapshot. */
    void pushStep(String traceId, TraceEvent event);

    /** Publish the final HTTP-response event and close the stream. */
    void pushDone(String traceId, FlowDoneEvent event);

    /** Publish a fatal-error event and close the stream. */
    void pushError(String traceId, String message);
}
