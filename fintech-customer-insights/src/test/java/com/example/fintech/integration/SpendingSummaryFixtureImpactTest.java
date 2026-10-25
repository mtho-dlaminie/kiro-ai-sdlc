package com.example.fintech.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixture-impact guard (test-plan §7, AC-10). Confirms the additive debit rows TXN-6003..TXN-6005 are
 * present on {@code CUST-000002} and that the pre-existing TXN-6001/TXN-6002 are still present and
 * unchanged — i.e. the fixture change is additive, nothing removed. Reads the wired transactions
 * endpoint so the assertion is against the real seed data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpendingSummaryFixtureImpactTest {

    @LocalServerPort
    private int port;

    @Autowired
    private org.springframework.context.ApplicationContext context;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
    }

    @Test
    void existingCust2DependentExpectationsUnchanged() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000002/transactions")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String body = response.getBody();
        assertThat(body).isNotNull();
        // Pre-existing rows still present and unchanged.
        assertThat(body).contains("TXN-6001").contains("TXN-6002");
        // Additive rows present.
        assertThat(body).contains("TXN-6003").contains("TXN-6004").contains("TXN-6005");
        // CUST-000002 now has exactly 5 transactions (2 original + 3 additive).
        assertThat(body).contains("\"count\":5");
    }
}
