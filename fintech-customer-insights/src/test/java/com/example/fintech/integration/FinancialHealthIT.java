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
 * RED-phase end-to-end coverage of the financial-health endpoint against the wired app + fixtures.
 *
 * <p>Mirrors {@code FinancialOverviewIT}: {@code RANDOM_PORT} + {@code RestClient}, real fixtures,
 * {@code DependencyFailureSeam} reset in {@link AfterEach}. Asserts the literal serialized JSON
 * strings (Jackson renders {@code BigDecimal} as strings). Fails until the endpoint is mapped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FinancialHealthIT {

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
    void knownCustomerBodyHasAllFields() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"customerId\"")
                .contains("\"currency\"")
                .contains("\"monthlyIncome\"")
                .contains("\"monthlyExpenses\"")
                .contains("\"savingsRate\"")
                .contains("\"debtToIncomeRatio\"")
                .contains("\"financialHealthScore\"")
                .contains("\"rating\"")
                .contains("\"recommendations\"")
                .contains("\"disclaimer\"");
    }

    @Test
    void cust1BodyExactStrings() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Baseline renders BigDecimal as JSON numbers (spec v3 §3.2), not quoted strings.
        assertThat(response.getBody())
                .contains("\"monthlyIncome\":2750.75")
                .contains("\"monthlyExpenses\":45.20")
                .contains("\"savingsRate\":0.9836")
                .contains("\"debtToIncomeRatio\":0.4362")
                .contains("\"currency\":\"GBP\"");
    }

    @Test
    void cust1ScoreIs82() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getBody())
                .contains("\"financialHealthScore\":82")
                .contains("\"rating\":\"EXCELLENT\"");
    }

    @Test
    void cust1BodyHasSyntheticDisclaimer() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getBody()).containsIgnoringCase("synthetic");
    }

    @Test
    void emptyDataCustomerReturns200NotFound() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000003/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Baseline renders BigDecimal as JSON numbers (spec v3 §3.2), not quoted strings.
        assertThat(response.getBody())
                .contains("\"monthlyIncome\":0.00")
                .contains("\"monthlyExpenses\":0.00")
                .contains("\"savingsRate\":0.0000")
                .contains("\"currency\":\"EUR\"");
    }

    @Test
    void unknownCustomerReturns404Problem() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-999999/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void malformedIdReturns400() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/oops/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void dependencyFailureReturns503Problem() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000004/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    }

    @Test
    void errorBodiesHaveNoStackTrace() {
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-999999/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getBody())
                .doesNotContain("at com.example.fintech")
                .doesNotContain("Exception:");
    }

    @Test
    void repeatedRequestIsDeterministic() {
        String first = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health")
                .retrieve().toEntity(String.class).getBody();
        String second = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health")
                .retrieve().toEntity(String.class).getBody();
        assertThat(first).isEqualTo(second);
    }

    // ---- date-range scoping (run-gh-2; AC-1/2/3/4/5/7) -------------------------------------------

    @Test
    void scopedWindow_returns200WithPinnedValues() {
        // AC-1: [2025-01-01, 2025-01-10] selects salary + groceries (excludes Jan-31 interest).
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health"
                        + "?from=2025-01-01T00:00:00Z&to=2025-01-10T00:00:00Z")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"monthlyIncome\":2500.00")
                .contains("\"monthlyExpenses\":45.20")
                .contains("\"savingsRate\":0.9819")
                .contains("\"debtToIncomeRatio\":0.4800")
                .contains("\"financialHealthScore\":81")
                .contains("\"rating\":\"EXCELLENT\"")
                .contains("\"currency\":\"GBP\"");
    }

    @Test
    void noParams_isByteForByteMergedExample() {
        // AC-2 / R1 regression: unscoped body is identical to the merged full-history example.
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"monthlyIncome\":2750.75")
                .contains("\"savingsRate\":0.9836")
                .contains("\"debtToIncomeRatio\":0.4362")
                .contains("\"financialHealthScore\":82")
                .contains("\"rating\":\"EXCELLENT\"");
    }

    @Test
    void singleBoundFrom_returns200ScopedFigures() {
        // AC-3: from=2025-01-10 only (open-ended upper) selects the Jan-31 interest only.
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health?from=2025-01-10T00:00:00Z")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"monthlyIncome\":250.75")
                .contains("\"monthlyExpenses\":0.00")
                .contains("\"savingsRate\":1.0000")
                .contains("\"debtToIncomeRatio\":4.7856")
                .contains("\"financialHealthScore\":66")
                .contains("\"rating\":\"GOOD\"");
    }

    @Test
    void emptyRange_returns200ZeroFigures() {
        // AC-7: a valid range selecting zero transactions → zero figures, DTI 9.9999, POOR; never 404.
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health"
                        + "?from=2024-01-01T00:00:00Z&to=2024-12-31T00:00:00Z")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .contains("\"monthlyIncome\":0.00")
                .contains("\"monthlyExpenses\":0.00")
                .contains("\"savingsRate\":0.0000")
                .contains("\"debtToIncomeRatio\":9.9999")
                .contains("\"financialHealthScore\":16")
                .contains("\"rating\":\"POOR\"");
    }

    @Test
    void fromAfterTo_returns400ProblemFieldFrom() {
        // AC-4: from strictly after to → 400 problem, type invalid-request, field "from".
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health"
                        + "?from=2025-02-01T00:00:00Z&to=2025-01-01T00:00:00Z")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .contains("urn:fintech:error:invalid-request")
                .contains("\"field\":\"from\"");
    }

    @Test
    void malformedDate_returns400Problem() {
        // AC-5: unparseable date → 400 problem, type invalid-request, naming the parameter.
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health?from=not-a-date")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody())
                .contains("urn:fintech:error:invalid-request")
                .contains("\"field\":\"from\"");
    }

    @Test
    void unknownCustomerWithRange_returns404Problem() {
        // AC-8: unknown customer with a range still 404 (range does not change the outcome).
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-999999/financial-health"
                        + "?from=2025-01-01T00:00:00Z&to=2025-01-10T00:00:00Z")
                .retrieve().toEntity(String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).contains("urn:fintech:error:customer-not-found");
    }

    @Test
    void scopedRequest_carriesCorrelationIdOnError() {
        // AC-12: an error path carries correlationId with no stack trace.
        ResponseEntity<String> response = client().get()
                .uri("/api/v1/customers/CUST-000001/financial-health"
                        + "?from=2025-02-01T00:00:00Z&to=2025-01-01T00:00:00Z")
                .retrieve().toEntity(String.class);
        assertThat(response.getBody())
                .contains("correlationId")
                .doesNotContain("at com.example.fintech");
    }
}
