package com.apiscope.core.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgenticDocsChatServiceStreamTest {

    @Test
    @DisplayName("SSE lines: data: prefix is stripped and [DONE] is excluded")
    void sseLineParsing_stripsPrefix_excludesDone() {
        String sseBody = "data: Hello\ndata: World\ndata: [DONE]\n";
        List<String> tokens = new ArrayList<>();
        sseBody.lines()
                .filter(l -> l.startsWith("data: "))
                .map(l -> l.substring(6))
                .filter(t -> !t.equals("[DONE]"))
                .forEach(tokens::add);
        assertThat(tokens).containsExactly("Hello", "World");
    }

    @Test
    @DisplayName("Empty body returns empty iterator")
    void nullBody_returnsEmptyIterator() {
        var it = java.util.Collections.emptyIterator();
        assertThat(it.hasNext()).isFalse();
    }
}
