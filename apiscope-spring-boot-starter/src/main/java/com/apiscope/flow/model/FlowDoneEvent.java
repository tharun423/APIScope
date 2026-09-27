package com.apiscope.flow.model;

public record FlowDoneEvent(
        String  traceId,
        int     httpStatus,
        String  responseBody,
        long    totalMs,
        boolean ok,
        int     stepCount
) {}
