package com.apiscope.core.chat;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import com.apiscope.core.port.LlmPort;
import com.apiscope.core.port.VectorStorePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgenticDocsChatServiceTest {

    @Mock private VectorStorePort vectorStorePort;
    @Mock private LlmPort llmPort;

    private AgenticDocsChatService service;

    @BeforeEach
    void setUp() {
        AgenticDocsProperties props = new AgenticDocsProperties(
                true, 5, null,
                "./apiscope-vector-store.json",
                new AgenticDocsProperties.RateLimit(true, 20),
                new AgenticDocsProperties.Cors(List.of("http://localhost:5173"))
        );
        @SuppressWarnings("unchecked")
        ObjectProvider<VectorStorePort> vsProvider = mock(ObjectProvider.class);
        when(vsProvider.getIfAvailable()).thenReturn(vectorStorePort);
        @SuppressWarnings("unchecked")
        ObjectProvider<LlmPort> llmProvider = mock(ObjectProvider.class);
        when(llmProvider.getIfAvailable()).thenReturn(llmPort);
        service = new AgenticDocsChatService(vsProvider, llmProvider, props);
    }

    @Test
    @DisplayName("answer() returns LLM response when context is found")
    void answer_returnsLlmResponse_whenContextFound() {
        when(vectorStorePort.findRelevantContext(anyString(), anyInt()))
                .thenReturn(List.of("POST /api/users - creates a new user"));
        when(llmPort.complete(anyString(), anyString(), anyString()))
                .thenReturn("Use POST /api/users with body {\"name\": \"John\"}");

        ChatResponse response = service.answer(new ChatRequest("How do I create a user?"));

        assertThat(response.answer()).isEqualTo("Use POST /api/users with body {\"name\": \"John\"}");
    }

    @Test
    @DisplayName("answer() returns fallback message when LLM returns null")
    void answer_returnsFallback_whenLlmReturnsNull() {
        when(vectorStorePort.findRelevantContext(anyString(), anyInt())).thenReturn(List.of());
        when(llmPort.complete(anyString(), anyString(), anyString())).thenReturn(null);

        ChatResponse response = service.answer(new ChatRequest("What is the meaning of life?"));

        assertThat(response.answer()).contains("I could not find a relevant endpoint");
    }

    @Test
    @DisplayName("answer() returns fallback message when LLM returns blank string")
    void answer_returnsFallback_whenLlmReturnsBlank() {
        when(vectorStorePort.findRelevantContext(anyString(), anyInt())).thenReturn(List.of());
        when(llmPort.complete(anyString(), anyString(), anyString())).thenReturn("   ");

        ChatResponse response = service.answer(new ChatRequest("What is the meaning of life?"));

        assertThat(response.answer()).contains("I could not find a relevant endpoint");
    }

    @Test
    @DisplayName("answer() calls vector store with the user question")
    void answer_callsVectorStore_withUserQuestion() {
        when(vectorStorePort.findRelevantContext(anyString(), anyInt())).thenReturn(List.of());
        when(llmPort.complete(anyString(), anyString(), anyString())).thenReturn("No match.");

        service.answer(new ChatRequest("How do I delete an account?"));

        verify(vectorStorePort, times(1)).findRelevantContext(anyString(), eq(5));
    }

}
