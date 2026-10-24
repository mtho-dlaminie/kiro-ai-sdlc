package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Synthetic credit status. This is synthetic data and does NOT represent real "
        + "creditworthiness or real banking records.")
public record CreditStatusResponse(
        @Schema(example = "CUST-000001") String customerId,
        @Schema(example = "742") int creditScore,
        @Schema(description = "Credit utilization as a percentage.", example = "18.50") BigDecimal creditUtilization,
        @Schema(example = "1200.00") BigDecimal outstandingDebt,
        @Schema(example = "GBP") String currency,
        @Schema(example = "CURRENT") String paymentStatus,
        @Schema(description = "Share of payments made on time (0..1).", example = "0.98") BigDecimal onTimePaymentRate,
        @Schema(example = "2") int openAccounts,
        @Schema(description = "Disclaimer clarifying the synthetic nature of this data.")
        String disclaimer) {
}
