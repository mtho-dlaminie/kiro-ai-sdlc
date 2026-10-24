package com.example.fintech.service;

import com.example.fintech.domain.Account;
import com.example.fintech.domain.AccountStatus;
import com.example.fintech.domain.AccountType;
import com.example.fintech.domain.CreditStatus;
import com.example.fintech.domain.Customer;
import com.example.fintech.domain.PaymentStatus;
import com.example.fintech.domain.Segment;
import com.example.fintech.domain.Transaction;
import com.example.fintech.domain.TransactionType;
import com.example.fintech.dto.FinancialOverviewResponse;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.repository.AccountRepository;
import com.example.fintech.repository.CreditStatusRepository;
import com.example.fintech.repository.CustomerRepository;
import com.example.fintech.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Aggregator unit test. Builds the service graph from real services over mocked repositories so the
 * aggregation (composition of all four views) and its negative paths are exercised directly.
 */
@ExtendWith(MockitoExtension.class)
class FinancialOverviewServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CreditStatusRepository creditStatusRepository = mock(CreditStatusRepository.class);

    private final FinancialOverviewService service = new FinancialOverviewService(
            new CustomerProfileService(customerRepository),
            new AccountService(customerRepository, accountRepository),
            new TransactionService(customerRepository, transactionRepository),
            new CreditStatusService(customerRepository, creditStatusRepository));

    private Customer knownCustomer(String id) {
        return new Customer(id, "Ada Lovelace", "ada.lovelace@example.com",
                LocalDate.of(1985, 12, 10), Segment.RETAIL, GBP);
    }

    @Test
    void aggregatesAllFourViews() {
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(knownCustomer("CUST-000001")));
        when(accountRepository.findByCustomerId("CUST-000001")).thenReturn(List.of(
                new Account("ACC-1001", "CUST-000001", AccountType.CHECKING, GBP,
                        new BigDecimal("1500.00"), new BigDecimal("1600.00"), AccountStatus.ACTIVE)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000001", null, null)).thenReturn(List.of(
                new Transaction("TXN-5002", "ACC-1001", "CUST-000001", new BigDecimal("2500.00"), GBP,
                        TransactionType.CREDIT, "SALARY",
                        OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), "Payroll")));
        when(creditStatusRepository.findByCustomerId("CUST-000001")).thenReturn(Optional.of(
                new CreditStatus("CUST-000001", 742, new BigDecimal("18.50"), new BigDecimal("1200.00"),
                        GBP, PaymentStatus.CURRENT, new BigDecimal("0.98"), 2)));

        FinancialOverviewResponse overview = service.getOverview("CUST-000001");

        assertThat(overview.profile().fullName()).isEqualTo("Ada Lovelace");
        assertThat(overview.accounts().count()).isEqualTo(1);
        assertThat(overview.transactions().count()).isEqualTo(1);
        assertThat(overview.creditStatus().creditScore()).isEqualTo(742);
        assertThat(overview.creditStatus().outstandingDebt()).isEqualByComparingTo(new BigDecimal("1200.00"));
        assertThat(overview.creditStatus().disclaimer()).containsIgnoringCase("synthetic");
    }

    @Test
    void aggregatesEmptyCollectionsForKnownCustomer() {
        when(customerRepository.findById("CUST-000003")).thenReturn(Optional.of(knownCustomer("CUST-000003")));
        when(accountRepository.findByCustomerId("CUST-000003")).thenReturn(List.of());
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000003", null, null)).thenReturn(List.of());
        when(creditStatusRepository.findByCustomerId("CUST-000003")).thenReturn(Optional.of(
                new CreditStatus("CUST-000003", 705, new BigDecimal("0.00"), new BigDecimal("0.00"),
                        GBP, PaymentStatus.CURRENT, new BigDecimal("1.00"), 0)));

        FinancialOverviewResponse overview = service.getOverview("CUST-000003");

        assertThat(overview.accounts().items()).isEmpty();
        assertThat(overview.transactions().items()).isEmpty();
    }

    @Test
    void throwsNotFoundForUnknownCustomer() {
        when(customerRepository.findById("CUST-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOverview("CUST-999999"))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void propagatesDependencyFailure() {
        when(customerRepository.findById("CUST-000004"))
                .thenThrow(new DependencyUnavailableException("customer-store"));

        assertThatThrownBy(() -> service.getOverview("CUST-000004"))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
