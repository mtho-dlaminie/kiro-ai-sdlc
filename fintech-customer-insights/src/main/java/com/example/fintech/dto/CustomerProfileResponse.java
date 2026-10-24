package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Synthetic customer profile. Does not represent a real person.")
public record CustomerProfileResponse(
        @Schema(example = "CUST-000001") String customerId,
        @Schema(example = "Ada Lovelace") String fullName,
        @Schema(example = "ada.lovelace@example.com") String email,
        @Schema(example = "1985-12-10") LocalDate dateOfBirth,
        @Schema(example = "RETAIL") String segment,
        @Schema(example = "GBP") String currency) {
}
