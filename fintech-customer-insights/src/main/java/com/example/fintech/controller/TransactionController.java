package com.example.fintech.controller;

import com.example.fintech.dto.DtoMapper;
import com.example.fintech.dto.TransactionsResponse;
import com.example.fintech.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@Validated
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "List customer transactions",
            description = "Returns the customer's synthetic transactions, optionally filtered by an inclusive "
                    + "ISO-8601 date range. A known customer with no matching transactions returns an empty list "
                    + "with status 200, never 404. If 'from' is after 'to', the request is rejected with 400.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transactions returned (possibly empty)"),
            @ApiResponse(responseCode = "400", description = "Malformed customer id or invalid date range",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Unknown customer",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
    })
    @GetMapping(value = "/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionsResponse getTransactions(
            @PathVariable @NotBlank @Pattern(regexp = "^CUST-\\d{6}$",
                    message = "customerId must match ^CUST-\\d{6}$") String customerId,
            @Parameter(description = "Inclusive lower bound (ISO-8601), e.g. 2025-01-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Inclusive upper bound (ISO-8601), e.g. 2025-12-31T23:59:59Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return DtoMapper.toTransactions(customerId, from, to,
                transactionService.getTransactions(customerId, from, to));
    }
}
