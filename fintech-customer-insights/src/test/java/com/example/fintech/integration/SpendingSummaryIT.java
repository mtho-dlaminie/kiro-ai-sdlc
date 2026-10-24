package com.example.fintech.integration;

import com.example.fintech.repository.inmemory.DependencyFailureSeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration tests (layer I) for the spending-summary endpoint against the wired app and
 * real in-memory fixtures (including additive TXN-6003..TXN-6005). Mirrors {@code FinancialHealthIT}:
 * {@code RANDOM_PORT} + {@code RestClient} with a pass-through status handler so non-2xx bodies are
 * readable. Asserts literal serialized money scale on the raw body.
 *
 * <p>RED phase: the {@code /spending-summary} route is unmapped until the controller exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpendingSummaryIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DependencyFailureSeam failureSeam;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
    }

    @AfterEach
    void resetSeam() {
        failureSeam.forceFailure(null);
    }

    private ResponseEntity<String> get(String uri) {
        return client().get().uri(uri).retrieve().toEntity(String.class);
    }

    // I1 — AC-1
    @Test
    void knownCustomerReturns200Json() {
        ResponseEntity<String> response = get("/api/v1/customers/CUST-000002/spending-summary");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isNotNull()
                .satisfies(ct -> assertThat(ct.isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
    }

    // I2 — AC-5 / AC-9 / AC-10
    @Test
    void cust2UnboundedOrderedTotals() {
        ResponseEntity<String> response = get("/api/v1/customers/CUST-000002/spending-summary");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        // Ordering: ELECTRONICS before GROCERIES before TRANSPORT (desc total, name asc on 200.00 tie).
        assertThat(body.indexOf("ELECTRONICS")).isLessThan(body.indexOf("GROCERIES"));
        assertThat(body.indexOf("GROCERIES")).isLessThan(body.indexOf("TRANSPORT"));
        assertThat(body)
                .contains("\"totalSpent\":850.00")
                .contains("\"totalSpent\":200.00")
                .contains("\"grandTotal\":1250.00")
                .contains("\"topCategory\":\"ELECTRONICS\"")
                .contains("\"debitTransactionCount\":4")
                .contains("\"currency\":\"USD\"");
        assertThat(body).containsIgnoringCase("synthetic");
    }

    // I3 — AC-7
    @Test
    void cust3EmptyShapeEur() {
        ResponseEntity<String> response = get("/api/v1/customers/CUST-000003/spending-summary");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body)
                .contains("\"categories\":[]")
                .contains("\"grandTotal\":0.00")
                .contains("\"topCategory\":null")
                .contains("\"debitTransactionCount\":0")
                .contains("\"currency\":\"EUR\"");
    }

    // I4 — AC-8
    @Test
    void unknownCustomerReturns404Problem() {
        ResponseEntity<String> response = get("/api/v1/customers/CUST-999999/spending-summary");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("urn:fintech:error:customer-not-found");
    }

    // I5 — AC-8
    @Test
    void dependencyFailureReturns503Problem() {
        ResponseEntity<String> response = get("/api/v1/customers/CUST-000004/spending-summary");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("urn:fintech:error:dependency-unavailable");
    }

    // I6 — AC-6 (inclusive boundary + from>to → 400)
    @Test
    void dateRangeInclusivityOnFixtureBoundary() {
        // TXN-6004 GROCERIES -80.00 on 2025-02-20; from=to exactly on that date includes it.
        ResponseEntity<String> boundary = get(
                "/api/v1/customers/CUST-000002/spending-summary"
                        + "?from=2025-02-20T00:00:00Z&to=2025-02-20T00:00:00Z");
        assertThat(boundary.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(boundary.getBody())
                .contains("\"category\":\"GROCERIES\"")
                .contains("\"grandTotal\":80.00")
                .contains("\"debitTransactionCount\":1");

        ResponseEntity<String> inverted = get(
                "/api/v1/customers/CUST-000002/spending-summary"
                        + "?from=2025-03-01T00:00:00Z&to=2025-01-01T00:00:00Z");
        assertThat(inverted.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(inverted.getBody()).contains("\"field\":\"from\"");
    }

    // I7 — AC-8
    @Test
    void malformedIdReturns400Problem() {
        ResponseEntity<String> response = get("/api/v1/customers/bad/spending-summary");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    // I8 — AC-8 (problem docs carry required fields, no stack trace)
    @Test
    void problemDocumentsCarryRequiredFieldsNoStackTrace() {
        for (String uri : new String[]{
                "/api/v1/customers/CUST-999999/spending-summary",
                "/api/v1/customers/CUST-000004/spending-summary"}) {
            ResponseEntity<String> response = get(uri);
            String body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body)
                    .contains("\"type\"")
                    .contains("\"title\"")
                    .contains("\"status\"")
                    .contains("\"detail\"")
                    .contains("\"correlationId\"");
            assertThat(body)
                    .doesNotContain("at com.example.fintech")
                    .doesNotContain("Exception:");
        }
    }

    // I9 — AC-9 (OpenAPI documents the path and its responses)
    @Test
    void openApiDocumentsSpendingSummaryPathAndResponses() {
        ResponseEntity<String> response = get("/v3/api-docs");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("/api/v1/customers/{customerId}/spending-summary");
        assertThat(body).contains("\"200\"").contains("\"400\"").contains("\"404\"").contains("\"503\"");
    }
}
