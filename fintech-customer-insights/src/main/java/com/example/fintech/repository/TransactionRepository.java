package com.example.fintech.repository;

import com.example.fintech.domain.Transaction;

import java.time.OffsetDateTime;
import java.util.List;

public interface TransactionRepository {

    List<Transaction> findByCustomerId(String customerId);

    /**
     * Returns transactions for the customer whose {@code transactionDate} falls within the inclusive
     * range. Null bounds are treated as open-ended.
     */
    List<Transaction> findByCustomerIdAndDateRange(String customerId, OffsetDateTime from, OffsetDateTime to);
}
