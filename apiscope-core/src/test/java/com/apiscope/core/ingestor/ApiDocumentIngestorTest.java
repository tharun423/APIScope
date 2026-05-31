package com.apiscope.core.ingestor;

import com.apiscope.core.config.AgenticDocsProperties;
import com.apiscope.core.scanner.ApiEndpointMetadata;
import com.apiscope.core.scanner.ApiScanCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiDocumentIngestorTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private ObjectProvider<VectorStore> vectorStoreProvider;

    private ApiDocumentIngestor ingestor;

    @BeforeEach
    void setUp() {
        when(vectorStoreProvider.getIfAvailable()).thenReturn(vectorStore);
        AgenticDocsProperties props = new AgenticDocsProperties(
                true, 5, null,
                "./nonexistent-test-store-XXXXXX.json",
                new AgenticDocsProperties.RateLimit(true, 20),
                new AgenticDocsProperties.Cors(List.of("http://localhost:5173"))
        );
        ingestor = new ApiDocumentIngestor(vectorStoreProvider, props);
    }

    private ApiScanCompletedEvent eventWith(List<ApiEndpointMetadata> endpoints) {
        return new ApiScanCompletedEvent(this, endpoints);
    }

    @Test
    @DisplayName("onScanCompleted() adds one Document per scanned endpoint")
    void onScanCompleted_addsOneDocumentPerEndpoint() {
        List<ApiEndpointMetadata> endpoints = List.of(
                new ApiEndpointMetadata("/api/users",    "GET",  "UserController",    "getUsers",    "List users",     List.of(), List.of(), List.of(), null, null),
                new ApiEndpointMetadata("/api/payments", "POST", "PaymentController", "makePayment", "Make a payment", List.of(), List.of(), List.of(), "PaymentRequest", "PaymentResponse")
        );

        ingestor.onScanCompleted(eventWith(endpoints));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("onScanCompleted() embeds endpoint details in the document text")
    void onScanCompleted_embeds_endpointDetails_inDocumentText() {
        List<ApiEndpointMetadata> endpoints = List.of(
                new ApiEndpointMetadata("/api/orders", "DELETE", "OrderController", "cancelOrder", "Cancel an order", List.of(), List.of(), List.of(), null, "void")
        );

        ingestor.onScanCompleted(eventWith(endpoints));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());

        String docText = captor.getValue().get(0).getText();
        assertThat(docText).contains("/api/orders");
        assertThat(docText).contains("DELETE");
        assertThat(docText).contains("Cancel an order");
    }

    @Test
    @DisplayName("onScanCompleted() does not call vectorStore when no endpoints are found")
    void onScanCompleted_skipsVectorStore_whenNoEndpoints() {
        ingestor.onScanCompleted(eventWith(List.of()));
        verify(vectorStore, never()).add(any());
    }

    @Test
    @DisplayName("onScanCompleted() is idempotent - second call is silently ignored")
    void onScanCompleted_isIdempotent_secondCallIgnored() {
        List<ApiEndpointMetadata> endpoints = List.of(
                new ApiEndpointMetadata("/api/users", "GET", "UserController", "getUsers", "List users", List.of(), List.of(), List.of(), null, null)
        );

        ingestor.onScanCompleted(eventWith(endpoints));
        ingestor.onScanCompleted(eventWith(endpoints));

        verify(vectorStore, times(1)).add(any());
    }

    @Test
    @DisplayName("onScanCompleted() continues gracefully when vectorStore.add() throws")
    void onScanCompleted_continuesGracefully_whenVectorStoreThrows() {
        List<ApiEndpointMetadata> endpoints = List.of(
                new ApiEndpointMetadata("/api/users", "GET", "UserController", "getUsers", "List users", List.of(), List.of(), List.of(), null, null)
        );
        doThrow(new RuntimeException("Embedding model not available")).when(vectorStore).add(any());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> ingestor.onScanCompleted(eventWith(endpoints)));
    }
}