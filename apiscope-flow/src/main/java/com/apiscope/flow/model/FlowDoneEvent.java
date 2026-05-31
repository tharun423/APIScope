package com.apiscope.flow.model;

/**
 * Final event pushed via SSE when the traced HTTP request has completed.
 * Sent as a named {@code done} SSE event after all {@link TraceEvent} steps.
 *
 * @param traceId      The trace identifier.
 * @param httpStatus   HTTP response status code returned by the host endpoint.
 * @param responseBody Raw response body (JSON or plain text).
 * @param totalMs      Wall-clock time for the entire chain (from execute call to response).
 * @param ok           {@code true} if {@code httpStatus} is 2xx.
 * @param stepCount    Total number of intercepted method calls recorded.
 */
public record FlowDoneEvent(
        String  traceId,
        int     httpStatus,
        String  responseBody,
        long    totalMs,
        boolean ok,
        int     stepCount
) {}
