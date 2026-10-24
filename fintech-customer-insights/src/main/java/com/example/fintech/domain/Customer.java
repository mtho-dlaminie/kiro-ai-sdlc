package com.example.fintech.domain;

import java.time.LocalDate;
import java.util.Currency;

/**
 * Immutable synthetic customer domain model.
 */
public record Customer(
        String customerId,
        String fullName,
        String email,
        LocalDate dateOfBirth,
        Segment segment,
        Currency currency) {
}
