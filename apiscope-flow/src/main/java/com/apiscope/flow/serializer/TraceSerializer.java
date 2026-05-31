package com.apiscope.flow.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Responsible solely for converting method arguments and return values to
 * capped JSON strings. Extracted from {@code FlowAspect} (SRP).
 */
@Component
public class TraceSerializer {

    /** Maximum JSON size for input/output fields (2 KB). */
    private static final int MAX_JSON_BYTES = 2048;

    private final ObjectMapper objectMapper;

    public TraceSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serialize a method-args array to a capped JSON string.
     * Falls back to {@link java.util.Arrays#toString} when Jackson fails.
     */
    public String serializeArgs(Object[] args) {
        if (args == null) return "null";
        try {
            return cap(objectMapper.writeValueAsString(args));
        } catch (Exception e) {
            return cap(java.util.Arrays.toString(args));
        }
    }

    /**
     * Serialize a single return value to a capped JSON string.
     * Falls back to {@link Object#toString} when Jackson fails.
     */
    public String serializeValue(Object value) {
        if (value == null) return "null";
        try {
            return cap(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            return cap(value.toString());
        }
    }

    /**
     * Build a concise error message from an exception, including up to 10
     * stack-trace lines.
     */
    public String buildErrorMessage(Throwable ex) {
        StringBuilder sb = new StringBuilder(ex.getClass().getName());
        if (ex.getMessage() != null) sb.append(": ").append(ex.getMessage());
        StackTraceElement[] trace = ex.getStackTrace();
        int lines = Math.min(trace.length, 10);
        for (int i = 0; i < lines; i++) {
            sb.append("\n  at ").append(trace[i]);
        }
        return sb.toString();
    }

    // ── Private ──────────────────────────────────────────────────────────────

    private String cap(String s) {
        if (s == null) return "null";
        return s.length() <= MAX_JSON_BYTES ? s : s.substring(0, MAX_JSON_BYTES) + "… [truncated]";
    }
}
