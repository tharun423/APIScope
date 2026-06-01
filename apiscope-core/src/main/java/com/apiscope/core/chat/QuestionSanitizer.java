package com.apiscope.core.chat;

import java.util.regex.Pattern;

/**
 * Sanitizes user questions before they reach the LLM.
 * Blocks prompt-injection attempts and enforces a max length.
 */
class QuestionSanitizer {

    private static final int MAX_LENGTH = 800;

    private static final Pattern INJECTION_PATTERN = Pattern.compile(
            "ignore.{0,20}(previous|above|all).{0,20}(instruction|prompt|rule|context)" +
            "|forget.{0,20}(instruction|rule|role|context)" +
            "|you are now" +
            "|pretend (you are|to be)" +
            "|act as (a|an|if)" +
            "|\\bDAN\\b" +
            "|jailbreak" +
            "|disregard.{0,20}(instruction|rule)" +
            "|reveal.{0,20}(prompt|instruction|system)" +
            "|override.{0,20}(instruction|rule)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final String BLOCKED = "[BLOCKED: prompt injection attempt detected]";

    /** Truncates to max length and blocks injection attempts. */
    static String sanitize(String raw) {
        if (raw == null) return "";
        String trimmed = raw.length() > MAX_LENGTH ? raw.substring(0, MAX_LENGTH) : raw;
        return INJECTION_PATTERN.matcher(trimmed).find() ? BLOCKED : trimmed;
    }
}
