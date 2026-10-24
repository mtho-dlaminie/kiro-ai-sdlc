package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable synthetic financial-health view for a customer. Composed from the existing profile,
 * account, transaction and credit-status services; no new data source is introduced.
 *
 * <p>All data is synthetic. {@code financialHealthScore} and {@code rating} are illustrative only and
 * do NOT represent real creditworthiness or real banking records.
 */
@Schema(description = "Synthetic financial-health summary derived from a customer's existing profile, "
        + "transactions and credit status. Illustrative only; does NOT represent real creditworthiness "
        + "or real banking records.",
        example = """
                {
                  "customerId": "CUST-000001",
                  "currency": "GBP",
                  "monthlyIncome": "2750.75",
                  "monthlyExpenses": "45.20",
                  "savingsRate": "0.9836",
                  "debtToIncomeRatio": "0.4362",
                  "financialHealthScore": 82,
                  "rating": "EXCELLENT",
                  "recommendations": [
                    "Your debt-to-income ratio is elevated; reducing outstanding balances could improve your score."
                  ],
                  "disclaimer": "Synthetic data for demonstration only. Does NOT represent real creditworthiness or real banking records."
                }
                """)
public record FinancialHealthResponse(
        @Schema(description = "Echo of the validated customer id.", example = "CUST-000001")
        String customerId,

        @Schema(description = "ISO 4217 currency code of the customer's profile.", example = "GBP")
        String currency,

        @Schema(description = "Derived monthly income (sum of CREDIT transactions), scale 2.", example = "2750.75")
        BigDecimal monthlyIncome,

        @Schema(description = "Derived monthly expenses (absolute sum of DEBIT transactions), scale 2.", example = "45.20")
        BigDecimal monthlyExpenses,

        @Schema(description = "Savings rate as a fraction in [0,1], scale 4.", example = "0.9836")
        BigDecimal savingsRate,

        @Schema(description = "Debt-to-income ratio as a fraction, scale 4. 9.9999 denotes undefined/capped-high.",
                example = "0.4362")
        BigDecimal debtToIncomeRatio,

        @Schema(description = "Illustrative synthetic health score, 0-100 inclusive.", example = "82")
        int financialHealthScore,

        @Schema(description = "Categorical rating: POOR, FAIR, GOOD, or EXCELLENT.", example = "EXCELLENT")
        String rating,

        @Schema(description = "Deterministic, ordered list of 0-3 generic synthetic recommendations.")
        List<String> recommendations,

        @Schema(description = "Synthetic-data disclaimer.",
                example = "Synthetic data for demonstration only. Does NOT represent real creditworthiness or real banking records.")
        String disclaimer) {
}
