package com.apiscope.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgenticDocsProperties}.
 *
 * <p>These tests verify that the default values are sensible.
 * No Spring context is started — this is a plain Java test that runs in milliseconds.</p>
 *
 * <h2>Why test defaults?</h2>
 * <p>If someone accidentally changes a default (e.g. sets topK to 0 or
 * clears the CORS origins list), these tests catch the regression
 * immediately — before it ever reaches a user.</p>
 *
 * <h2>Record note</h2>
 * <p>{@link AgenticDocsProperties} is an immutable record — there are no setters.
 * Default values are enforced via {@code @DefaultValue} and verified here.
 * The canonical constructor is used to build test instances with custom values.</p>
 */
class AgenticDocsPropertiesTest {

    /** Convenience factory: creates properties with all defaults. */
    private AgenticDocsProperties defaults() {
        return new AgenticDocsProperties(
                true,
                5,
                null,
                "./apiscope-vector-store.json",
                new AgenticDocsProperties.RateLimit(true, 20),
                new AgenticDocsProperties.Cors(List.of("http://localhost:5173"))
        );
    }

    @Test
    @DisplayName("default enabled is true")
    void defaults_enabled_isTrue() {
        assertThat(defaults().enabled()).isTrue();
    }

    @Test
    @DisplayName("default topK is 5")
    void defaults_topK_isFive() {
        // topK=5 is a balanced default: enough context for the LLM without too many tokens
        assertThat(defaults().topK()).isEqualTo(5);
    }

    @Test
    @DisplayName("default systemPrompt is null (uses built-in prompt in service)")
    void defaults_systemPrompt_isNull() {
        // null means "use the DEFAULT_SYSTEM_PROMPT from AgenticDocsChatService"
        assertThat(defaults().systemPrompt()).isNull();
    }

    @Test
    @DisplayName("default CORS allowed-origins contains localhost:5173")
    void defaults_cors_containsLocalhostVite() {
        // Vite dev server runs on 5173 by default
        assertThat(defaults().cors().allowedOrigins())
                .contains("http://localhost:5173");
    }

    @Test
    @DisplayName("custom topK value is stored correctly")
    void customTopK_isStoredCorrectly() {
        AgenticDocsProperties props = new AgenticDocsProperties(true, 10, null,
                "./apiscope-vector-store.json",
                new AgenticDocsProperties.RateLimit(true, 20),
                new AgenticDocsProperties.Cors(List.of("http://localhost:5173")));
        assertThat(props.topK()).isEqualTo(10);
    }

    @Test
    @DisplayName("custom systemPrompt value is stored correctly")
    void customSystemPrompt_isStoredCorrectly() {
        AgenticDocsProperties props = new AgenticDocsProperties(true, 5,
                "My custom prompt with {context}",
                "./apiscope-vector-store.json",
                new AgenticDocsProperties.RateLimit(true, 20),
                new AgenticDocsProperties.Cors(List.of("http://localhost:5173")));
        assertThat(props.systemPrompt()).isEqualTo("My custom prompt with {context}");
    }

    @Test
    @DisplayName("custom CORS allowed origins are stored correctly")
    void customCors_isStoredCorrectly() {
        AgenticDocsProperties.Cors cors = new AgenticDocsProperties.Cors(
                List.of("https://myapp.com", "https://staging.myapp.com"));
        AgenticDocsProperties props = new AgenticDocsProperties(true, 5, null,
                "./apiscope-vector-store.json",
                new AgenticDocsProperties.RateLimit(true, 20), cors);

        assertThat(props.cors().allowedOrigins())
                .containsExactlyInAnyOrder("https://myapp.com", "https://staging.myapp.com");
    }

    @Test
    @DisplayName("enabled=false is stored correctly")
    void enabled_false_isStoredCorrectly() {
        AgenticDocsProperties props = new AgenticDocsProperties(false, 5, null,
                "./apiscope-vector-store.json",
                new AgenticDocsProperties.RateLimit(true, 20),
                new AgenticDocsProperties.Cors(List.of("http://localhost:5173")));
        assertThat(props.enabled()).isFalse();
    }
}

