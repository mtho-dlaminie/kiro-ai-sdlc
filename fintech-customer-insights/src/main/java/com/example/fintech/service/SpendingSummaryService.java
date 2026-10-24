package com.example.fintech.service;

import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Transaction;
import com.example.fintech.domain.TransactionType;
import com.example.fintech.dto.CategorySpend;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.InvalidRequestException;
import com.example.fintech.repository.CustomerRepository;
import com.example.fintech.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debit-only spending aggregation. Pure business logic over the customer's transactions: filter to
 * debits, sum positive magnitudes per category, order deterministically, and derive the grand total,
 * top category and debit count. A known customer with no spending yields the empty shape (200, never
 * 404). All monetary values are {@link BigDecimal} scale 2 with {@link RoundingMode#HALF_EVEN}.
 */
@Service
public class SpendingSummaryService {

    private final CustomerRepository customerRepository;
    private final TransactionRepository transactionRepository;

    public SpendingSummaryService(CustomerRepository customerRepository,
                                  TransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Summarizes a customer's debit spending over an optional inclusive date range. Validates
     * {@code from <= to} first, then existence (which also surfaces the dependency-failure seam), then
     * aggregates the repository's candidates.
     */
    public Result summarize(String customerId, OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be before or equal to 'to'", "from");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));

        List<Transaction> candidates =
                transactionRepository.findByCustomerIdAndDateRange(customerId, from, to);

        Map<String, BigDecimal> totalsByCategory = new LinkedHashMap<>();
        int debitCount = 0;
        for (Transaction txn : candidates) {
            if (txn.type() != TransactionType.DEBIT) {
                continue;
            }
            debitCount++;
            BigDecimal magnitude = txn.amount().abs().setScale(2, RoundingMode.HALF_EVEN);
            totalsByCategory.merge(txn.category(), magnitude, BigDecimal::add);
        }

        List<CategorySpend> categories = new ArrayList<>(totalsByCategory.size());
        for (Map.Entry<String, BigDecimal> entry : totalsByCategory.entrySet()) {
            categories.add(new CategorySpend(entry.getKey(),
                    entry.getValue().setScale(2, RoundingMode.HALF_EVEN)));
        }
        categories.sort(Comparator.comparing(CategorySpend::totalSpent).reversed()
                .thenComparing(CategorySpend::category));

        BigDecimal grandTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);
        for (CategorySpend category : categories) {
            grandTotal = grandTotal.add(category.totalSpent());
        }
        grandTotal = grandTotal.setScale(2, RoundingMode.HALF_EVEN);

        String topCategory = categories.isEmpty() ? null : categories.getFirst().category();

        return new Result(customer.currency().getCurrencyCode(), List.copyOf(categories),
                grandTotal, topCategory, debitCount);
    }

    /**
     * Internal aggregation carrier consumed by {@code DtoMapper.toSpendingSummary}. Not an API type.
     */
    public record Result(String currency, List<CategorySpend> categories, BigDecimal grandTotal,
                         String topCategory, int debitTransactionCount) {
    }
}
