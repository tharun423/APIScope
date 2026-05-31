package com.apiscope.flow.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local collector for SQL statements intercepted by {@link FlowStatementInspector}.
 *
 * <p>Usage pattern inside {@code FlowAspect}:
 * <pre>
 *   SqlCapture.begin();
 *   try {
 *       Object result = pjp.proceed();
 *       List&lt;String&gt; queries = SqlCapture.drain();
 *       ...
 *   } finally {
 *       SqlCapture.drain(); // ensure cleanup on any code path
 *   }
 * </pre>
 *
 * <p>{@link #add} is a no-op when no capture session is active, so the
 * Hibernate interceptor never pays a cost outside of a traced request.
 */
public final class SqlCapture {

    private static final ThreadLocal<List<String>> QUERIES = new ThreadLocal<>();

    private SqlCapture() {}

    /** Start a new capture session for the current thread. */
    public static void begin() {
        QUERIES.set(new ArrayList<>());
    }

    /**
     * Record one SQL statement. No-op when called outside an active capture session
     * (e.g. normal, non-traced requests).
     */
    public static void add(String sql) {
        List<String> list = QUERIES.get();
        if (list != null) {
            list.add(sql);
        }
    }

    /**
     * Return all collected SQL statements and end the capture session.
     * Always safe to call; returns an empty list when no session was started.
     */
    public static List<String> drain() {
        List<String> list = QUERIES.get();
        QUERIES.remove();
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }

    /** {@code true} when a capture session is active on the current thread. */
    public static boolean isCapturing() {
        return QUERIES.get() != null;
    }
}
