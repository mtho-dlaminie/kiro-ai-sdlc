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
 * End-to-end coverage of the aggregator endpoint against the wired app + fixtures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinancialOverviewIT {

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
    void aggregatesAllViewsForKnownCustomer() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-overview")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"profile\"")
                .contains("\"accounts\"")
                .contains("\"transactions\"")
                .contains("\"creditStatus\"")
                .contains("Ada Lovelace")
                .contains("742");
    }

    @Test
    void aggregatesEmptyCollectionsForKnownCustomer() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000003/financial-overview")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"items\":[]");
    }

    @Test
    void returns404ForUnknownCustomer() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-999999/financial-overview")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void returns400ForMalformedCustomerId() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/oops/financial-overview")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void returns503ForDependencyFailure() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000004/financial-overview")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
