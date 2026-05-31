package com.apiscope.core.scanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ApiMetadataScanner}.
 *
 * <h2>What is tested here?</h2>
 * <ul>
 *   <li>Initial state — endpoints list is empty before the context refresh event fires.</li>
 *   <li>Idempotency guard — calling {@code onApplicationEvent} twice must only trigger
 *       one real scan (protects against duplicate Spring context events).</li>
 *   <li>Unmodifiable result — callers cannot mutate the returned list.</li>
 *   <li>Empty handler map — graceful handling when no endpoints are registered.</li>
 * </ul>
 *
 * <p>Full end-to-end scanning of real {@code @RestController} endpoints is covered by
 * the integration test ({@code AgenticDocsIntegrationTest}) in the sample-app module.</p>
 */
@ExtendWith(MockitoExtension.class)
class ApiMetadataScannerTest {

    @Mock
    private RequestMappingHandlerMapping handlerMapping;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ContextRefreshedEvent event;

    private ApiMetadataScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new ApiMetadataScanner(handlerMapping, eventPublisher);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getScannedEndpoints() returns empty list before the context refresh event fires")
    void getScannedEndpoints_returnsEmpty_beforeScan() {
        // No event has been fired yet — list must be empty, not null
        assertThat(scanner.getScannedEndpoints())
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("onApplicationEvent() queries the handler mapping and stores results")
    void onApplicationEvent_queriesHandlerMapping_andStoresResults() {
        // GIVEN: no handler methods registered
        when(handlerMapping.getHandlerMethods()).thenReturn(Collections.emptyMap());

        // WHEN: context refresh fires
        scanner.onApplicationEvent(event);

        // THEN: the mapping was queried exactly once
        verify(handlerMapping, times(1)).getHandlerMethods();
        // AND: the resulting list (empty here) is accessible
        assertThat(scanner.getScannedEndpoints()).isEmpty();
    }

    @Test
    @DisplayName("onApplicationEvent() is idempotent — only the first call triggers a scan")
    void onApplicationEvent_isIdempotent_onMultipleCalls() {
        // GIVEN: no handler methods registered
        when(handlerMapping.getHandlerMethods()).thenReturn(Collections.emptyMap());

        // WHEN: the event fires twice (parent + child context refresh is common in Spring)
        scanner.onApplicationEvent(event);
        scanner.onApplicationEvent(event);

        // THEN: the underlying mapping is queried exactly once — the second call is a no-op
        verify(handlerMapping, times(1)).getHandlerMethods();
    }

    @Test
    @DisplayName("getScannedEndpoints() returns an unmodifiable list")
    void getScannedEndpoints_returnsUnmodifiableList() {
        // GIVEN: scanning has completed
        when(handlerMapping.getHandlerMethods()).thenReturn(Collections.emptyMap());
        scanner.onApplicationEvent(event);

        // WHEN: we get the list and try to add to it
        List<ApiEndpointMetadata> endpoints = scanner.getScannedEndpoints();

        // THEN: it is unmodifiable — external code cannot corrupt internal state
        assertThat(endpoints).isUnmodifiable();
    }

    @Test
    @DisplayName("getScannedEndpoints() returns the same list reference after scanning")
    void getScannedEndpoints_returnsSameList_afterScan() {
        // GIVEN: scanning has completed
        when(handlerMapping.getHandlerMethods()).thenReturn(Collections.emptyMap());
        scanner.onApplicationEvent(event);

        // WHEN: called multiple times
        List<ApiEndpointMetadata> first  = scanner.getScannedEndpoints();
        List<ApiEndpointMetadata> second = scanner.getScannedEndpoints();

        // THEN: the same list is returned — no repeated scanning or re-allocation
        assertThat(first).isSameAs(second);
    }
}
