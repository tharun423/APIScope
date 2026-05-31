package com.apiscope.flow.model;

import java.util.List;

/**
 * A single method-call snapshot captured by {@link com.apiscope.flow.aspect.FlowAspect}.
 *
 * @param traceId      Unique ID for this execution trace.
 * @param stepIndex    Zero-based position in the execution chain.
 * @param layer        Bean stereotype: CONTROLLER | SERVICE | REPOSITORY | COMPONENT.
 * @param className    Simple class name of the intercepted bean.
 * @param methodName   Method name.
 * @param inputJson    JSON-serialized method arguments (capped at 2 KB).
 * @param outputJson   JSON-serialized return value (capped at 2 KB).
 * @param durationMs   Wall-clock time for the method call in milliseconds.
 * @param status       {@code EXIT} on success, {@code ERROR} on exception.
 * @param errorMessage Exception message when {@code status=ERROR}; null otherwise.
 * @param sqlQueries   SQL statements intercepted via Hibernate during this method call.
 *                     Empty when the host app does not use JPA or no queries were issued.
 */
public record TraceEvent(
        String       traceId,
        int          stepIndex,
        String       layer,
        String       className,
        String       methodName,
        String       inputJson,
        String       outputJson,
        long         durationMs,
        String       status,
        String       errorMessage,
        List<String> sqlQueries
) {}
