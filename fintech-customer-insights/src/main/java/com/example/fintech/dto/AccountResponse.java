package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Synthetic account.")
public record AccountResponse(
        @Schema(example = "ACC-1001") String accountId,
        @Schema(example = "CHECKING") String type,
        @Schema(example = "GBP") String currency,
        @Schema(example = "1500.00") BigDecimal availableBalance,
        @Schema(example = "1600.00") BigDecimal currentBalance,
        @Schema(example = "ACTIVE") String status) {
}
