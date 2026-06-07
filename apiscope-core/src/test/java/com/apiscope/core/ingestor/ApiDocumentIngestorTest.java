package com.apiscope.core.ingestor;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.ApiScanCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ApiDocumentIngestorTest {

    private MockRestServiceServer mockServer;
    private ApiDocumentIngestor ingestor;

    @BeforeEach
    void setUp() throws Exception {
        RestTemplate rt = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(rt);

        AgenticDocsProperties props = new AgenticDocsProperties(
                true, "http://localhost:8000",
                new AgenticDocsProperties.RateLimit(false, 20),
                new AgenticDocsProperties.Cors(List.of())
        );
        ingestor = new ApiDocumentIngestor(props);

        // Replace the internal http field with mock-backed RestClient
        var field = ApiDocumentIngestor.class.getDeclaredField("http");
        field.setAccessible(true);
        field.set(ingestor, RestClient.builder(rt).baseUrl("http://localhost:8000").build());
    }

    private ApiScanCompletedEvent event(List<ApiEndpointMetadata> endpoints) {
        return new ApiScanCompletedEvent(this, endpoints);
    }

    private ApiEndpointMetadata ep(String path, String method) {
        return new ApiEndpointMetadata(path, method, "Ctrl", "m", "desc",
                List.of(), List.of(), List.of(), null, null);
    }

    @Test
    @DisplayName("onScanCompleted() POSTs to /ingest")
    void postsToIngest() {
        mockServer.expect(requestTo("http://localhost:8000/ingest"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ingestor.onScanCompleted(event(List.of(ep("/api/users", "GET"))));
        mockServer.verify();
    }

    @Test
    @DisplayName("onScanCompleted() skips POST when no endpoints")
    void skipsWhenEmpty() {
        ingestor.onScanCompleted(event(List.of()));
        mockServer.verify();
    }

    @Test
    @DisplayName("onScanCompleted() is idempotent — second call ignored")
    void isIdempotent() {
        mockServer.expect(requestTo("http://localhost:8000/ingest"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        ingestor.onScanCompleted(event(List.of(ep("/api/x", "GET"))));
        ingestor.onScanCompleted(event(List.of(ep("/api/x", "GET"))));
        mockServer.verify();
    }

    @Test
    @DisplayName("Handles HTTP error from LLM service gracefully")
    void handlesErrorGracefully() {
        mockServer.expect(requestTo("http://localhost:8000/ingest"))
                .andRespond(withServerError());

        assertDoesNotThrow(() -> ingestor.onScanCompleted(event(List.of(ep("/api/x", "GET")))));
    }
}
