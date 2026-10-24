package com.example.fintech.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregated synthetic view combining profile, accounts, transactions and credit status. "
        + "Composed from existing services; no new orchestration infrastructure.")
public record FinancialOverviewResponse(
        CustomerProfileResponse profile,
        AccountsResponse accounts,
        TransactionsResponse transactions,
        CreditStatusResponse creditStatus) {
}
