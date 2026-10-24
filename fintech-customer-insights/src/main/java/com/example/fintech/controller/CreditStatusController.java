package com.example.fintech.controller;

import com.example.fintech.dto.CreditStatusResponse;
import com.example.fintech.dto.DtoMapper;
import com.example.fintech.service.CreditStatusService;
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
public class CreditStatusController {

    private final CreditStatusService creditStatusService;

    public CreditStatusController(CreditStatusService creditStatusService) {
        this.creditStatusService = creditStatusService;
    }

    @Operation(summary = "Get customer credit status",
            description = "Returns synthetic credit indicators for the customer. This is synthetic data and "
                    + "does NOT represent real creditworthiness or real banking records.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credit status found"),
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
    @GetMapping(value = "/credit-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public CreditStatusResponse getCreditStatus(
            @PathVariable @NotBlank @Pattern(regexp = "^CUST-\\d{6}$",
                    message = "customerId must match ^CUST-\\d{6}$") String customerId) {
        return DtoMapper.toCreditStatus(creditStatusService.getCreditStatus(customerId));
    }
}
