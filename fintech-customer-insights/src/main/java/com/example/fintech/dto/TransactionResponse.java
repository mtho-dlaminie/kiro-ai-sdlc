package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Schema(description = "Synthetic transaction. Amount is signed: positive for CREDIT, negative for DEBIT.")
public record TransactionResponse(
        @Schema(example = "TXN-5001") String transactionId,
        @Schema(example = "ACC-1001") String accountId,
        @Schema(example = "-45.20") BigDecimal amount,
        @Schema(example = "GBP") String currency,
        @Schema(example = "DEBIT") String type,
        @Schema(example = "GROCERIES") String category,
        @Schema(example = "2025-01-05T00:00:00Z") OffsetDateTime transactionDate,
        @Schema(example = "Supermarket") String description) {
}
