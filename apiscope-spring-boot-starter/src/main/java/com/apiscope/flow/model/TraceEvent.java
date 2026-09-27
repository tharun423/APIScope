package com.apiscope.flow.model;

import java.util.List;

public record TraceEvent(
        String       traceId,
        int          stepIndex,
        String       layer,       // CONTROLLER | SERVICE | REPOSITORY | COMPONENT
        String       className,
        String       methodName,
        String       inputJson,
        String       outputJson,
        long         durationMs,
        String       status,      // EXIT | ERROR
        String       errorMessage,
        List<String> sqlQueries
) {}
