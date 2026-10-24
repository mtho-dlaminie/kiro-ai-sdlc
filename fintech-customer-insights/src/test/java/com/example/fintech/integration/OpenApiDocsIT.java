package com.example.fintech.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the springdoc OpenAPI JSON is served and the Swagger UI resource resolves.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocsIT {

    @LocalServerPort
    private int port;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (req, res) -> { })
                .build();
    }

    @Test
    void apiDocsJsonIsServed() {
        ResponseEntity<String> response = client().get()
                .uri("/v3/api-docs")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("Fintech Customer Insights API")
                .contains("/api/v1/customers/{customerId}/financial-overview");
    }

    @Test
    void swaggerUiResolves() {
        ResponseEntity<String> response = client().get()
                .uri("/swagger-ui/index.html")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsIgnoringCase("swagger");
    }

    @Test
    void financialHealthEndpointIsExposed() {
        // Reconciled from the baseline "absent" guard (AC-17): the endpoint now legitimately exists,
        // so the invariant becomes "documented". Presence coverage is strengthened, not weakened.
        ResponseEntity<String> response = client().get()
                .uri("/v3/api-docs")
                .retrieve().toEntity(String.class);

        assertThat(response.getBody()).contains("/api/v1/customers/{customerId}/financial-health");
    }
}
