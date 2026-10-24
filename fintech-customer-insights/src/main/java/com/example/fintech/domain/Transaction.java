package com.example.fintech.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Currency;

/**
 * Immutable transaction domain model. Amount is a signed {@link BigDecimal}; positive for CREDIT,
 * negative for DEBIT.
 */
public record Transaction(
        String transactionId,
        String accountId,
        String customerId,
        BigDecimal amount,
        Currency currency,
        TransactionType type,
        String category,
        OffsetDateTime transactionDate,
        String description) {
}
