package com.apiscope.flow.sql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-local SQL collector. FlowAspect calls begin() before a method,
 * then drain() after — pairing them ensures no ThreadLocal leak.
 */
public final class SqlCapture {

    private static final ThreadLocal<List<String>> QUERIES = new ThreadLocal<>();

    private SqlCapture() {}

    public static void begin() {
        QUERIES.set(new ArrayList<>());
    }

    /** No-op when no capture session is active (normal non-traced requests). */
    public static void add(String sql) {
        List<String> list = QUERIES.get();
        if (list != null) list.add(sql);
    }

    /** Returns collected queries and ends the session. Always safe to call. */
    public static List<String> drain() {
        List<String> list = QUERIES.get();
        QUERIES.remove();
        return list != null ? Collections.unmodifiableList(list) : List.of();
    }
}
