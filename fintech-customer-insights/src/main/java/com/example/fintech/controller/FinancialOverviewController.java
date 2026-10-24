package com.example.fintech.controller;

import com.example.fintech.dto.FinancialOverviewResponse;
import com.example.fintech.service.FinancialOverviewService;
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
public class FinancialOverviewController {

    private final FinancialOverviewService overviewService;

    public FinancialOverviewController(FinancialOverviewService overviewService) {
        this.overviewService = overviewService;
    }

    @Operation(summary = "Get aggregated financial overview",
            description = "Aggregates the customer's synthetic profile, accounts, transactions and credit status "
                    + "into a single view. All data is synthetic and does NOT represent real creditworthiness or "
                    + "real banking records.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aggregated overview returned"),
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
    @GetMapping(value = "/financial-overview", produces = MediaType.APPLICATION_JSON_VALUE)
    public FinancialOverviewResponse getOverview(
            @PathVariable @NotBlank @Pattern(regexp = "^CUST-\\d{6}$",
                    message = "customerId must match ^CUST-\\d{6}$") String customerId) {
        return overviewService.getOverview(customerId);
    }
}
