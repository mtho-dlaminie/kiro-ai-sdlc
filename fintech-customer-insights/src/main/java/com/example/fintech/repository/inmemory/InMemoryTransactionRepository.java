package com.example.fintech.repository.inmemory;

import com.example.fintech.domain.Transaction;
import com.example.fintech.fixtures.FixtureData;
import com.example.fintech.repository.TransactionRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final FixtureData fixtures;
    private final DependencyFailureSeam failureSeam;

    public InMemoryTransactionRepository(FixtureData fixtures, DependencyFailureSeam failureSeam) {
        this.fixtures = fixtures;
        this.failureSeam = failureSeam;
    }

    @Override
    public List<Transaction> findByCustomerId(String customerId) {
        failureSeam.checkAvailability(customerId, "transaction-store");
        return fixtures.transactionsByCustomer().getOrDefault(customerId, List.of());
    }

    @Override
    public List<Transaction> findByCustomerIdAndDateRange(String customerId, OffsetDateTime from, OffsetDateTime to) {
        return findByCustomerId(customerId).stream()
                .filter(txn -> from == null || !txn.transactionDate().isBefore(from))
                .filter(txn -> to == null || !txn.transactionDate().isAfter(to))
                .toList();
    }
}
