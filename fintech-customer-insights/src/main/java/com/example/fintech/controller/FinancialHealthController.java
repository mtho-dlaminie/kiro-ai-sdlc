package com.example.fintech.controller;

import com.example.fintech.dto.FinancialHealthResponse;
import com.example.fintech.service.FinancialHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@Validated
public class FinancialHealthController {

    private final FinancialHealthService healthService;

    public FinancialHealthController(FinancialHealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(summary = "Get synthetic financial-health summary",
            description = "Derives a synthetic financial-health view (monthly income/expenses, savings rate, "
                    + "debt-to-income ratio, an illustrative 0-100 score, a categorical rating and generic "
                    + "recommendations) from the customer's existing synthetic data. All values are synthetic "
                    + "and do NOT represent real creditworthiness or real banking records.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Financial-health summary returned",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = FinancialHealthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed customer id",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Unknown customer",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    })
    @GetMapping(value = "/financial-health", produces = MediaType.APPLICATION_JSON_VALUE)
    public FinancialHealthResponse getHealth(
            @PathVariable @NotBlank @Pattern(regexp = "^CUST-\\d{6}$",
                    message = "customerId must match ^CUST-\\d{6}$") String customerId) {
        return healthService.getHealth(customerId);
    }
}
