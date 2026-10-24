package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Collection of transactions for a customer, optionally filtered by an ISO-8601 date range. "
        + "Empty collections return an empty list, never 404.")
public record TransactionsResponse(
        @Schema(example = "CUST-000001") String customerId,
        @Schema(description = "Echoed lower bound of the requested range, if any.") OffsetDateTime from,
        @Schema(description = "Echoed upper bound of the requested range, if any.") OffsetDateTime to,
        int count,
        List<TransactionResponse> items) {
}
