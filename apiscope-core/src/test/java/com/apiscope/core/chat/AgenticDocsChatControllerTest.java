package com.apiscope.core.chat;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.model.ChatRequest;
import com.apiscope.core.model.ChatResponse;
import com.apiscope.core.ratelimit.RateLimiterService;
import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.EndpointRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for AgenticDocsChatController.
 * Gap 1+5: controller injects ChatPort; rate limiting moved to interceptor.
 */
@WebMvcTest(AgenticDocsChatController.class)
@Import(AgenticDocsChatControllerTest.TestConfig.class)
class AgenticDocsChatControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        AgenticDocsProperties agenticDocsProperties() {
            return new AgenticDocsProperties(
                    true, 5, null,
                    "./apiscope-vector-store.json",
                    new AgenticDocsProperties.RateLimit(true, 20),
                    new AgenticDocsProperties.Cors(List.of("http://localhost:5173"))
            );
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Gap 5: ChatPort replaces the separate ChatService mock. */
    @MockitoBean
    private ChatPort chatPort;

    @MockitoBean
    private EndpointRepository endpointRepository;

    /**
     * Gap 1: RateLimiterService is still mocked because the interceptor
     * (registered by AgenticDocsMvcConfigurer) depends on it.
     * The controller itself no longer references it.
     */
    @MockitoBean
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void allowRateLimitByDefault() {
        when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("POST /chat with valid question returns 200 and LLM answer")
    void postChat_withValidQuestion_returns200() throws Exception {
        when(chatPort.answer(any())).thenReturn(new ChatResponse("Use POST /api/users"));

        mockMvc.perform(post("/apiscope/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("How do I create a user?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Use POST /api/users"));
    }

    @Test
    @DisplayName("POST /chat with blank question returns 400 Bad Request")
    void postChat_withBlankQuestion_returns400() throws Exception {
        mockMvc.perform(post("/apiscope/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /chat with null question returns 400 Bad Request")
    void postChat_withNullQuestion_returns400() throws Exception {
        mockMvc.perform(post("/apiscope/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /chat returns 429 when rate limit exceeded (interceptor-level)")
    void postChat_returns429_whenRateLimitExceeded() throws Exception {
        when(rateLimiterService.tryConsume(anyString())).thenReturn(false);

        mockMvc.perform(post("/apiscope/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("How do I create a user?"))))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("GET /chat returns 405 Method Not Allowed")
    void getChat_returns405() throws Exception {
        mockMvc.perform(get("/apiscope/api/chat"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("GET /endpoints returns 200 with the list of scanned endpoints")
    void getEndpoints_returns200WithEndpointList() throws Exception {
        ApiEndpointMetadata fakeEndpoint =
                new ApiEndpointMetadata("/api/users", "GET", "UserController", "getAllUsers",
                        "List all users", List.of(), List.of(), List.of(), null, null);
        when(endpointRepository.getScannedEndpoints()).thenReturn(List.of(fakeEndpoint));

        mockMvc.perform(get("/apiscope/api/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("/api/users"))
                .andExpect(jsonPath("$[0].httpMethod").value("GET"))
                .andExpect(jsonPath("$[0].controllerName").value("UserController"));
    }

    @Test
    @DisplayName("GET /endpoints returns 200 with empty list when none scanned")
    void getEndpoints_returnsEmptyList_whenNoneScanned() throws Exception {
        when(endpointRepository.getScannedEndpoints()).thenReturn(List.of());

        mockMvc.perform(get("/apiscope/api/endpoints"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}