package com.example.fintech.service;

import com.example.fintech.domain.Transaction;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.InvalidRequestException;
import com.example.fintech.repository.CustomerRepository;
import com.example.fintech.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TransactionService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(CustomerRepository customerRepository,
                              TransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Returns the customer's transactions, optionally filtered by an inclusive date range. A known
     * customer with no matching transactions yields an empty list (200); an unknown customer yields
     * {@link CustomerNotFoundException} (404); {@code from} after {@code to} yields
     * {@link InvalidRequestException} (400).
     */
    public List<Transaction> getTransactions(String customerId, OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be before or equal to 'to'", "from");
        }
        if (customerRepository.findById(customerId).isEmpty()) {
            throw new CustomerNotFoundException(customerId);
        }
        return transactionRepository.findByCustomerIdAndDateRange(customerId, from, to);
    }
}
