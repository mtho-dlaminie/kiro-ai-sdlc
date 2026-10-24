package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Total spend (positive magnitude) for a single transaction category.")
public record CategorySpend(
        @Schema(example = "GROCERIES") String category,
        @Schema(description = "Sum of debit magnitudes for this category, BigDecimal scale 2.",
                example = "200.00") BigDecimal totalSpent) {
}
