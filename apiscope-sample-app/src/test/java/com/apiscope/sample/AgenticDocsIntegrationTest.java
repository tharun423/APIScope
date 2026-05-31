package com.apiscope.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test that starts a full Spring Boot application context and verifies
 * that the Agentic Docs starter wires up correctly end-to-end.
 *
 * <h2>Why this test matters</h2>
 * <p>Unit tests (with Mockito) check individual classes in isolation. This integration
 * test checks that all the pieces connect correctly — auto-configuration, component
 * scanning, MVC routing, CORS configuration, and Bean Validation all fire in the right
 * order when a real Spring context starts.</p>
 *
 * <h2>How LLM dependencies are handled</h2>
 * <p>{@code @MockBean} replaces the real {@link VectorStore} and {@link ChatClient.Builder}
 * beans with no-op mocks <em>before</em> the context is fully started.
 * This means no real LLM API key or Ollama instance is needed to run these tests —
 * they can run in CI with zero external dependencies.</p>
 *
 * <h2>Key scenarios tested</h2>
 * <ul>
 *   <li>The auto-configuration registers the endpoints controller correctly.</li>
 *   <li>The endpoints listing API returns HTTP 200 with a JSON array.</li>
 *   <li>Bean Validation rejects blank questions with HTTP 400.</li>
 *   <li>GET on the chat endpoint returns HTTP 405 (POST-only).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(AgenticDocsIntegrationTest.TestBeans.class)
@TestPropertySource(properties = {
        "spring.profiles.active=ollama",
        // Disable Ollama auto-configuration so no real connection is attempted in CI.
        // The ChatClient.Builder bean is prototype-scoped (Spring AI) and cannot be
        // overridden with @MockitoBean — we provide a manual mock via @TestConfiguration instead.
        "spring.ai.ollama.chat.enabled=false",
        "spring.ai.ollama.embedding.enabled=false"
})
class AgenticDocsIntegrationTest {

    /**
     * Provides singleton mock beans for the LLM infrastructure so no real Ollama
     * instance or API key is required during CI.
     *
     * <p>We use a {@code @TestConfiguration} instead of {@code @MockitoBean} because
     * Spring AI's {@code ChatClient.Builder} is prototype-scoped and cannot be overridden
     * by {@code @MockitoBean} (which only works with singleton beans).</p>
     */
    @TestConfiguration
    static class TestBeans {
        @Bean
        public ChatClient.Builder chatClientBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            ChatClient client = mock(ChatClient.class);
            org.mockito.Mockito.when(builder.build()).thenReturn(client);
            return builder;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    /**
     * Replaced with a mock so no EmbeddingModel or real vector DB is needed.
     * {@code VectorStoreConfig} is {@code @ConditionalOnMissingBean}, so this mock
     * takes precedence and the real {@code SimpleVectorStore} is never created.
     */
    @MockitoBean
    private VectorStore vectorStore;

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /apiscope/api/endpoints — returns HTTP 200 with JSON array")
    void endpoints_returnsOkWithJsonArray() throws Exception {
        mockMvc.perform(get("/apiscope/api/endpoints")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /apiscope/api/chat — returns HTTP 405 (endpoint is POST-only)")
    void chatGet_returns405_methodNotAllowed() throws Exception {
        mockMvc.perform(get("/apiscope/api/chat")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("POST /apiscope/api/chat with blank question — returns HTTP 400 (Bean Validation)")
    void chatPost_withBlankQuestion_returns400() throws Exception {
        mockMvc.perform(post("/apiscope/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /apiscope/api/chat with missing question field — returns HTTP 400")
    void chatPost_withMissingQuestion_returns400() throws Exception {
        mockMvc.perform(post("/apiscope/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /apiscope/api/chat with whitespace-only question — returns HTTP 400")
    void chatPost_withWhitespaceOnlyQuestion_returns400() throws Exception {
        mockMvc.perform(post("/apiscope/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
