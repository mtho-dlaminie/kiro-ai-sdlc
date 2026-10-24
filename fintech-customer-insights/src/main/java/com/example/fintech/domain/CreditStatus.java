package com.example.fintech.domain;

import java.math.BigDecimal;
import java.util.Currency;

/**
 * Immutable synthetic credit status domain model. This is synthetic data and does NOT represent
 * real creditworthiness or real banking records.
 */
public record CreditStatus(
        String customerId,
        int creditScore,
        BigDecimal creditUtilization,
        BigDecimal outstandingDebt,
        Currency currency,
        PaymentStatus paymentStatus,
        BigDecimal onTimePaymentRate,
        int openAccounts) {
}
