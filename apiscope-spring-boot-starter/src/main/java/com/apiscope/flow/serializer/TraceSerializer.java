package com.apiscope.flow.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class TraceSerializer {

    private static final int MAX_BYTES = 2048;

    private final ObjectMapper objectMapper;

    public TraceSerializer(@Qualifier("flowObjectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serializeArgs(Object[] args) {
        if (args == null) return "null";
        try {
            return cap(objectMapper.writeValueAsString(args));
        } catch (Exception e) {
            return cap(java.util.Arrays.toString(args));
        }
    }

    public String serializeValue(Object value) {
        if (value == null) return "null";
        try {
            return cap(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            return cap(value.toString());
        }
    }

    public String errorMessage(Throwable ex) {
        StringBuilder sb = new StringBuilder(ex.getClass().getName());
        if (ex.getMessage() != null) sb.append(": ").append(ex.getMessage());
        StackTraceElement[] trace = ex.getStackTrace();
        for (int i = 0; i < Math.min(trace.length, 3); i++) {
            sb.append("\n  at ").append(trace[i]);
        }
        return sb.toString();
    }

    private String cap(String s) {
        return s != null && s.length() > MAX_BYTES ? s.substring(0, MAX_BYTES) + "… [truncated]" : s;
    }
}
