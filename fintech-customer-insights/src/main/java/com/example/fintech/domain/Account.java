package com.example.fintech.domain;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Immutable account domain model. Monetary values are {@link BigDecimal} with an explicit currency.
 */
public record Account(
        String accountId,
        String customerId,
        AccountType type,
        Currency currency,
        BigDecimal availableBalance,
        BigDecimal currentBalance,
        AccountStatus status) {
}
