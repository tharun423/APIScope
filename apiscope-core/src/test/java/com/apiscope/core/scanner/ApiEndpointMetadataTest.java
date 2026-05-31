package com.apiscope.core.scanner;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ApiEndpointMetadata}.
 *
 * <p>These tests verify that the record's data is stored correctly and
 * that {@link ApiEndpointMetadata#toLlmReadableText()} produces output
 * that contains all required pieces of information for the LLM prompt.</p>
 *
 * <p>Because {@code ApiEndpointMetadata} is a pure Java record (no Spring,
 * no database, no external calls), these tests are extremely fast — no
 * annotations beyond {@code @Test} are needed.</p>
 */
class ApiEndpointMetadataTest {

    @Test
    @DisplayName("record stores all constructor values correctly")
    void record_storesAllValues() {
        // GIVEN: an endpoint metadata object
        ApiEndpointMetadata meta = new ApiEndpointMetadata(
                "/api/users",   // path
                "POST",         // httpMethod
                "UserController", // controllerName
                "createUser",   // methodName
                "Create a new user account", // description
                List.of("userId"),            // pathParams
                List.of("page", "size"),       // requiredQueryParams
                List.of(),                     // optionalQueryParams
                "CreateUserRequest",           // requestBodyType
                "UserResponse"                 // responseType
        );

        // THEN: all fields are readable via the generated record accessors
        assertThat(meta.path()).isEqualTo("/api/users");
        assertThat(meta.httpMethod()).isEqualTo("POST");
        assertThat(meta.controllerName()).isEqualTo("UserController");
        assertThat(meta.methodName()).isEqualTo("createUser");
        assertThat(meta.description()).isEqualTo("Create a new user account");
        assertThat(meta.pathParams()).containsExactly("userId");
        assertThat(meta.requiredQueryParams()).containsExactly("page", "size");
        assertThat(meta.requestBodyType()).isEqualTo("CreateUserRequest");
        assertThat(meta.responseType()).isEqualTo("UserResponse");
    }

    @Test
    @DisplayName("toLlmReadableText() contains all key endpoint details")
    void toLlmReadableText_containsAllDetails() {
        // GIVEN: an endpoint metadata object
        ApiEndpointMetadata meta = new ApiEndpointMetadata(
                "/api/payments", "DELETE", "PaymentController", "cancelPayment", "Cancel a payment",
                List.of(), List.of(), List.of(), null, "void"
        );

        // WHEN: we generate the LLM-readable text
        String text = meta.toLlmReadableText();

        // THEN: all important details appear in the output.
        // The LLM relies on this text to answer questions correctly.
        assertThat(text).contains("/api/payments");
        assertThat(text).contains("DELETE");
        assertThat(text).contains("PaymentController");
        assertThat(text).contains("cancelPayment");
        assertThat(text).contains("Cancel a payment");
    }

    @Test
    @DisplayName("toLlmReadableText() includes HTTP method and path in the same line")
    void toLlmReadableText_includesMethodAndPathTogether() {
        // GIVEN: a GET endpoint
        ApiEndpointMetadata meta = new ApiEndpointMetadata(
                "/api/orders", "GET", "OrderController", "listOrders", "List all orders",
                List.of(), List.of(), List.of(), null, "List"
        );

        // WHEN
        String text = meta.toLlmReadableText();

        // THEN: the method and path appear together (as [GET] /api/orders)
        // This is important for the LLM to understand it as a single endpoint identity.
        assertThat(text).contains("[GET]");
        assertThat(text).contains("/api/orders");
    }

    @Test
    @DisplayName("two records with the same values are equal")
    void twoIdenticalRecords_areEqual() {
        // Java records automatically generate equals() based on all fields.
        ApiEndpointMetadata a = new ApiEndpointMetadata("/x", "GET", "C", "m", "desc", List.of(), List.of(), List.of(), null, null);
        ApiEndpointMetadata b = new ApiEndpointMetadata("/x", "GET", "C", "m", "desc", List.of(), List.of(), List.of(), null, null);

        assertThat(a).isEqualTo(b);
    }
}
