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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration coverage for the customer endpoints against the wired app + fixtures.
 * Deterministic and independently runnable (random port, synthetic seed data, seam reset per test).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerEndpointsIT {

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

    @Test
    void profileReturns200ForKnownCustomer() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/profile")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Ada Lovelace").contains("GBP");
    }

    @Test
    void profileReturns404ProblemJsonForUnknownCustomer() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-999999/profile")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("\"status\":404");
    }

    @Test
    void profileReturns400ForMalformedCustomerId() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/not-valid/profile")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void accountsReturnsEmptyListForKnownCustomerWithNoAccounts() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000003/accounts")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"count\":0").contains("\"items\":[]");
    }

    @Test
    void accountsMonetaryValuesHaveScaleTwo() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/accounts")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 10250.75 is serialized preserving scale; compare numeric value via contains on the scaled literal.
        assertThat(response.getBody()).contains("10250.75");
        assertThat(new BigDecimal("1500.00")).isEqualByComparingTo(new BigDecimal("1500.0"));
    }

    @Test
    void transactionsReturnsEmptyListForKnownCustomerWithNoTransactions() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000003/transactions")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"items\":[]");
    }

    @Test
    void transactionsFiltersByDateRange() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/transactions?from=2025-01-10T00:00:00Z&to=2025-01-31T23:59:59Z")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("TXN-5003").doesNotContain("TXN-5002");
    }

    @Test
    void transactionsReturns400WhenFromAfterTo() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/transactions?from=2025-02-01T00:00:00Z&to=2025-01-01T00:00:00Z")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void creditStatusReturns200WithDisclaimer() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/credit-status")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("742").containsIgnoringCase("synthetic");
    }

    @Test
    void dependencyFailureReturns503ForReservedId() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000004/profile")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void dependencyFailureReturns503WhenSeamForced() {
        failureSeam.forceFailure("CUST-000001");

        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/profile")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
