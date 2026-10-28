package com.example.fintech.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED-phase OpenAPI/API-contract coverage for the financial-health endpoint (AC-16, AC-17).
 *
 * <p>This is a NEW presence assertion. The existing {@code OpenApiDocsIT.financialHealthEndpointIsNotExposed}
 * "absent" guard is intentionally left untouched in the RED phase; it is reconciled to a presence
 * assertion during GREEN, alongside the controller addition (design §2.3 / R-2, AC-17), so the suite
 * is never inconsistent and the absent-guard is flipped rather than deleted. Keeping this as a
 * separate file lets the RED phase show the correct failure (endpoint absent) without weakening the
 * baseline guard before the feature legitimately exists.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinancialHealthOpenApiIT {

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
    }

    @Test
    void financialHealthEndpointIsExposed() {
        ResponseEntity<String> response = client().get()
                .uri("/v3/api-docs")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("/api/v1/customers/{customerId}/financial-health");
    }

    @Test
    void apiDocsDocumentsFinancialHealthResponses() {
        ResponseEntity<String> response = client().get()
                .uri("/v3/api-docs")
                .retrieve().toEntity(String.class);
        String body = response.getBody();
        assertThat(body).contains("/api/v1/customers/{customerId}/financial-health");
        // Documented status codes for the endpoint (AC-16) and the synthetic disclaimer.
        assertThat(body)
                .contains("\"200\"")
                .contains("\"400\"")
                .contains("\"404\"")
                .contains("\"503\"");
        assertThat(body).containsIgnoringCase("synthetic");
    }

    @Test
    void operationDocumentsFromAndToParams() throws Exception {
        // AC-10: the financial-health operation lists from and to query params with descriptions +
        // ISO-8601 examples. Parse the OpenAPI JSON and inspect the operation's parameters precisely.
        ResponseEntity<String> response = client().get()
                .uri("/v3/api-docs")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        com.fasterxml.jackson.databind.JsonNode root =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
        com.fasterxml.jackson.databind.JsonNode params = root
                .path("paths")
                .path("/api/v1/customers/{customerId}/financial-health")
                .path("get")
                .path("parameters");
        assertThat(params.isArray()).isTrue();

        java.util.Map<String, com.fasterxml.jackson.databind.JsonNode> byName = new java.util.HashMap<>();
        params.forEach(p -> byName.put(p.path("name").asText(), p));

        assertThat(byName).containsKeys("from", "to");
        for (String name : new String[] {"from", "to"}) {
            com.fasterxml.jackson.databind.JsonNode p = byName.get(name);
            assertThat(p.path("in").asText()).isEqualTo("query");
            assertThat(p.path("required").asBoolean(true)).isFalse();
            assertThat(p.path("description").asText()).isNotBlank();
            assertThat(p.path("description").asText()).contains("2025-01");
        }
    }
}
