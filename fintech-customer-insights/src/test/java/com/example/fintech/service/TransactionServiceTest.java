package com.example.fintech.service;

import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Segment;
import com.example.fintech.domain.Transaction;
import com.example.fintech.domain.TransactionType;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.InvalidRequestException;
import com.example.fintech.repository.CustomerRepository;
import com.example.fintech.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService service;

    private Customer knownCustomer(String id) {
        return new Customer(id, "Ada", "ada@example.com", LocalDate.of(1985, 12, 10), Segment.RETAIL, GBP);
    }

    private Transaction txn(String id, BigDecimal amount, OffsetDateTime date) {
        return new Transaction(id, "ACC-1001", "CUST-000001", amount, GBP, TransactionType.CREDIT,
                "SALARY", date, "Payroll");
    }

    @Test
    void returnsTransactionsForKnownCustomer() {
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(knownCustomer("CUST-000001")));
        Transaction t = txn("TXN-5002", new BigDecimal("2500.00"),
                OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000001", null, null)).thenReturn(List.of(t));

        List<Transaction> result = service.getTransactions("CUST-000001", null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().amount()).isEqualByComparingTo(new BigDecimal("2500.00"));
    }

    @Test
    void returnsEmptyListForKnownCustomerWithNoTransactions() {
        when(customerRepository.findById("CUST-000003")).thenReturn(Optional.of(knownCustomer("CUST-000003")));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000003", null, null)).thenReturn(List.of());

        assertThat(service.getTransactions("CUST-000003", null, null)).isEmpty();
    }

    @Test
    void filtersByDateRange() {
        OffsetDateTime from = OffsetDateTime.of(2025, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2025, 1, 31, 0, 0, 0, 0, ZoneOffset.UTC);
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(knownCustomer("CUST-000001")));
        Transaction inRange = txn("TXN-5003", new BigDecimal("250.75"),
                OffsetDateTime.of(2025, 1, 31, 0, 0, 0, 0, ZoneOffset.UTC));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000001", from, to)).thenReturn(List.of(inRange));

        List<Transaction> result = service.getTransactions("CUST-000001", from, to);

        assertThat(result).extracting(Transaction::transactionId).containsExactly("TXN-5003");
    }

    @Test
    void throwsNotFoundForUnknownCustomer() {
        when(customerRepository.findById("CUST-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTransactions("CUST-999999", null, null))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void throwsInvalidRequestWhenFromAfterTo() {
        OffsetDateTime from = OffsetDateTime.of(2025, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        lenient().when(customerRepository.findById("CUST-000001"))
                .thenReturn(Optional.of(knownCustomer("CUST-000001")));

        assertThatThrownBy(() -> service.getTransactions("CUST-000001", from, to))
                .isInstanceOf(InvalidRequestException.class);
    }
}
