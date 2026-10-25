package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Debit-only spending summary for a customer, grouped by category over an optional "
        + "inclusive ISO-8601 date range. Empty results return a 200 with an empty categories array, never 404. "
        + "Synthetic data only.")
public record SpendingSummaryResponse(
        @Schema(example = "CUST-000002") String customerId,
        @Schema(description = "ISO 4217 code of the customer's profile currency.", example = "USD") String currency,
        @Schema(description = "Echoed lower bound of the requested range, null if omitted.") OffsetDateTime from,
        @Schema(description = "Echoed upper bound of the requested range, null if omitted.") OffsetDateTime to,
        @Schema(description = "Per-category totals, ordered by total descending then category name ascending.")
        List<CategorySpend> categories,
        @Schema(description = "Sum of all category totals, BigDecimal scale 2.", example = "1250.00")
        BigDecimal grandTotal,
        @Schema(description = "Category with the highest total, null when there is no spending.",
                example = "ELECTRONICS") String topCategory,
        @Schema(description = "Count of included debit transactions (not categories).", example = "4")
        int debitTransactionCount,
        @Schema(description = "Mandatory synthetic-data disclaimer.") String disclaimer) {
}
