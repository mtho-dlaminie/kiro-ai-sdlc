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
import com.example.fintech.dto.DtoMapper;
import com.example.fintech.dto.FinancialHealthResponse;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.exception.InvalidRequestException;
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
 * RED-phase unit tests for the financial-health aggregator (spec v2, BR-1..BR-10).
 *
 * <p>Builds the real {@code FinancialHealthService} over the four real services wired to mocked
 * repositories, mirroring {@code FinancialOverviewServiceTest}, so the derivation arithmetic and its
 * negative paths are exercised directly. All data is synthetic. {@link BigDecimal} assertions use
 * {@code isEqualByComparingTo}, never unscaled {@code equals} (testing-standards).
 *
 * <p>These tests are written before production code (TDD RED). The feature classes
 * ({@code FinancialHealthService}, {@code FinancialHealthResponse}) do not exist yet, so this class
 * fails to resolve — the correct RED signal (missing functionality, not broken infra).
 */
@ExtendWith(MockitoExtension.class)
class FinancialHealthServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final CreditStatusRepository creditStatusRepository = mock(CreditStatusRepository.class);

    private final FinancialHealthService service = new FinancialHealthService(
            new CustomerProfileService(customerRepository),
            new AccountService(customerRepository, accountRepository),
            new TransactionService(customerRepository, transactionRepository),
            new CreditStatusService(customerRepository, creditStatusRepository));

    // ---- fixture builders (synthetic) ------------------------------------------------------------

    private Customer customer(String id, Currency currency) {
        return new Customer(id, "Ada Lovelace", "ada.lovelace@example.com",
                LocalDate.of(1985, 12, 10), Segment.RETAIL, currency);
    }

    private Transaction creditTxn(String id, String customerId, String amount, Currency currency) {
        return new Transaction(id, "ACC-X", customerId, new BigDecimal(amount), currency,
                TransactionType.CREDIT, "SALARY",
                OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), "Payroll");
    }

    private Transaction debitTxn(String id, String customerId, String amount, Currency currency) {
        return new Transaction(id, "ACC-X", customerId, new BigDecimal(amount), currency,
                TransactionType.DEBIT, "GROCERIES",
                OffsetDateTime.of(2025, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC), "Supermarket");
    }

    private CreditStatus creditStatus(String customerId, String utilization, String debt, Currency currency) {
        return new CreditStatus(customerId, 700, new BigDecimal(utilization), new BigDecimal(debt),
                currency, PaymentStatus.CURRENT, new BigDecimal("0.98"), 2);
    }

    private Account account(String id, String customerId, Currency currency) {
        return new Account(id, customerId, AccountType.CHECKING, currency,
                new BigDecimal("100.00"), new BigDecimal("100.00"), AccountStatus.ACTIVE);
    }

    /** Wires repositories for one of the three canonical fixture customers. */
    private void stubCust1() {
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(customer("CUST-000001", GBP)));
        when(accountRepository.findByCustomerId("CUST-000001")).thenReturn(List.of(account("ACC-1001", "CUST-000001", GBP)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000001", null, null)).thenReturn(List.of(
                creditTxn("TXN-5002", "CUST-000001", "2500.00", GBP),
                creditTxn("TXN-5003", "CUST-000001", "250.75", GBP),
                debitTxn("TXN-5001", "CUST-000001", "-45.20", GBP)));
        when(creditStatusRepository.findByCustomerId("CUST-000001")).thenReturn(Optional.of(
                creditStatus("CUST-000001", "18.50", "1200.00", GBP)));
    }

    private void stubCust2() {
        when(customerRepository.findById("CUST-000002")).thenReturn(Optional.of(customer("CUST-000002", USD)));
        when(accountRepository.findByCustomerId("CUST-000002")).thenReturn(List.of(account("ACC-2001", "CUST-000002", USD)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000002", null, null)).thenReturn(List.of(
                creditTxn("TXN-6002", "CUST-000002", "1800.00", USD),
                debitTxn("TXN-6001", "CUST-000002", "-850.00", USD)));
        when(creditStatusRepository.findByCustomerId("CUST-000002")).thenReturn(Optional.of(
                creditStatus("CUST-000002", "42.75", "850.00", USD)));
    }

    private void stubCust3Empty() {
        when(customerRepository.findById("CUST-000003")).thenReturn(Optional.of(customer("CUST-000003", EUR)));
        when(accountRepository.findByCustomerId("CUST-000003")).thenReturn(List.of());
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000003", null, null)).thenReturn(List.of());
        when(creditStatusRepository.findByCustomerId("CUST-000003")).thenReturn(Optional.of(
                creditStatus("CUST-000003", "0.00", "0.00", EUR)));
    }

    // ---- positive / composition (AC-2, AC-3) -----------------------------------------------------

    @Test
    void composesFourExistingServices() {
        stubCust1();
        FinancialHealthResponse health = service.getHealth("CUST-000001");
        assertThat(health).isNotNull();
        assertThat(health.customerId()).isEqualTo("CUST-000001");
    }

    @Test
    void responseContainsAllTenFields() {
        stubCust1();
        FinancialHealthResponse h = service.getHealth("CUST-000001");
        assertThat(h.customerId()).isEqualTo("CUST-000001");
        assertThat(h.currency()).isNotBlank();
        assertThat(h.monthlyIncome()).isNotNull();
        assertThat(h.monthlyExpenses()).isNotNull();
        assertThat(h.savingsRate()).isNotNull();
        assertThat(h.debtToIncomeRatio()).isNotNull();
        assertThat(h.financialHealthScore()).isBetween(0, 100);
        assertThat(h.rating()).isNotBlank();
        assertThat(h.recommendations()).isNotNull();
        assertThat(h.disclaimer()).isNotBlank();
    }

    // ---- BR-1 / BR-2 income & expenses (AC-4) ----------------------------------------------------

    @Test
    void incomeAndExpenses_cust1() {
        stubCust1();
        FinancialHealthResponse h = service.getHealth("CUST-000001");
        assertThat(h.monthlyIncome()).isEqualByComparingTo("2750.75");
        assertThat(h.monthlyIncome().scale()).isEqualTo(2);
        assertThat(h.monthlyExpenses()).isEqualByComparingTo("45.20");
        assertThat(h.monthlyExpenses().scale()).isEqualTo(2);
    }

    @Test
    void incomeAndExpenses_cust2() {
        stubCust2();
        FinancialHealthResponse h = service.getHealth("CUST-000002");
        assertThat(h.monthlyIncome()).isEqualByComparingTo("1800.00");
        assertThat(h.monthlyExpenses()).isEqualByComparingTo("850.00");
    }

    @Test
    void incomeAndExpenses_cust3Empty() {
        stubCust3Empty();
        FinancialHealthResponse h = service.getHealth("CUST-000003");
        assertThat(h.monthlyIncome()).isEqualByComparingTo("0.00");
        assertThat(h.monthlyExpenses()).isEqualByComparingTo("0.00");
    }

    // ---- BR-3 savings rate (AC-5) ----------------------------------------------------------------

    @Test
    void savingsRate_cust1() {
        stubCust1();
        assertThat(service.getHealth("CUST-000001").savingsRate()).isEqualByComparingTo("0.9836");
    }

    @Test
    void savingsRate_cust2() {
        stubCust2();
        assertThat(service.getHealth("CUST-000002").savingsRate()).isEqualByComparingTo("0.5278");
    }

    @Test
    void savingsRate_zeroIncomeIsZero() {
        stubCust3Empty();
        assertThat(service.getHealth("CUST-000003").savingsRate()).isEqualByComparingTo("0.0000");
    }

    // ---- BR-4 debt-to-income (AC-6) --------------------------------------------------------------

    @Test
    void dti_cust1() {
        stubCust1();
        assertThat(service.getHealth("CUST-000001").debtToIncomeRatio()).isEqualByComparingTo("0.4362");
    }

    @Test
    void dti_cust2() {
        stubCust2();
        assertThat(service.getHealth("CUST-000002").debtToIncomeRatio()).isEqualByComparingTo("0.4722");
    }

    @Test
    void dti_zeroIncomeZeroDebtIsZero() {
        stubCust3Empty();
        assertThat(service.getHealth("CUST-000003").debtToIncomeRatio()).isEqualByComparingTo("0.0000");
    }

    @Test
    void dti_zeroIncomeWithDebtIsSentinel() {
        // income 0 (no credits) but outstanding debt > 0 → 9.9999 sentinel (BR-4 edge).
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(customer("CUST-000001", GBP)));
        when(accountRepository.findByCustomerId("CUST-000001")).thenReturn(List.of());
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000001", null, null)).thenReturn(List.of());
        when(creditStatusRepository.findByCustomerId("CUST-000001")).thenReturn(Optional.of(
                creditStatus("CUST-000001", "0.00", "500.00", GBP)));
        assertThat(service.getHealth("CUST-000001").debtToIncomeRatio()).isEqualByComparingTo("9.9999");
    }

    // ---- BR-5 score (AC-7) -----------------------------------------------------------------------

    @Test
    void score_cust1Is82() {
        stubCust1();
        assertThat(service.getHealth("CUST-000001").financialHealthScore()).isEqualTo(82);
    }

    @Test
    void score_cust2Is54() {
        stubCust2();
        assertThat(service.getHealth("CUST-000002").financialHealthScore()).isEqualTo(54);
    }

    @Test
    void score_cust3Is50() {
        stubCust3Empty();
        assertThat(service.getHealth("CUST-000003").financialHealthScore()).isEqualTo(50);
    }

    // ---- BR-6 rating + boundaries (AC-8) ---------------------------------------------------------

    @Test
    void rating_cust1Excellent() {
        stubCust1();
        assertThat(service.getHealth("CUST-000001").rating()).isEqualTo("EXCELLENT");
    }

    @Test
    void rating_cust2Fair() {
        stubCust2();
        assertThat(service.getHealth("CUST-000002").rating()).isEqualTo("FAIR");
    }

    @Test
    void rating_cust3Fair() {
        stubCust3Empty();
        assertThat(service.getHealth("CUST-000003").rating()).isEqualTo("FAIR");
    }

    /**
     * Band boundaries are inclusive-lower (BR-6: 0-39 POOR, 40-59 FAIR, 60-79 GOOD, 80-100 EXCELLENT).
     * CUST-000003 scores exactly 50 (FAIR) and CUST-000001 scores exactly 82 (EXCELLENT); these pin
     * the FAIR and EXCELLENT bands with real fixture-derived scores.
     */
    @Test
    void ratingBoundary_fairBandPinnedAt50() {
        stubCust3Empty();
        FinancialHealthResponse h = service.getHealth("CUST-000003");
        assertThat(h.financialHealthScore()).isEqualTo(50);
        assertThat(h.rating()).isEqualTo("FAIR");
    }

    // ---- BR-7 currency + invariant (AC-10, ERR-4) ------------------------------------------------

    @Test
    void currency_cust1Gbp_cust2Usd_cust3Eur() {
        stubCust1();
        stubCust2();
        stubCust3Empty();
        assertThat(service.getHealth("CUST-000001").currency()).isEqualTo("GBP");
        assertThat(service.getHealth("CUST-000002").currency()).isEqualTo("USD");
        assertThat(service.getHealth("CUST-000003").currency()).isEqualTo("EUR");
    }

    @Test
    void mixedCurrencyThrowsInvalidRequest() {
        // profile GBP, but a transaction is in USD → single-currency invariant violated (BR-7).
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(customer("CUST-000001", GBP)));
        when(accountRepository.findByCustomerId("CUST-000001")).thenReturn(List.of(account("ACC-1001", "CUST-000001", GBP)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000001", null, null)).thenReturn(List.of(
                creditTxn("TXN-5002", "CUST-000001", "2500.00", USD)));
        when(creditStatusRepository.findByCustomerId("CUST-000001")).thenReturn(Optional.of(
                creditStatus("CUST-000001", "18.50", "1200.00", GBP)));
        assertThatThrownBy(() -> service.getHealth("CUST-000001"))
                .isInstanceOf(InvalidRequestException.class);
    }

    // ---- BR-8 recommendations (AC-9) -------------------------------------------------------------

    @Test
    void recommendations_cust1DebtOnly() {
        stubCust1();
        List<String> recs = service.getHealth("CUST-000001").recommendations();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0)).containsIgnoringCase("debt-to-income");
    }

    @Test
    void recommendations_cust2DebtThenUtil() {
        stubCust2();
        List<String> recs = service.getHealth("CUST-000002").recommendations();
        assertThat(recs).hasSize(2);
        assertThat(recs.get(0)).containsIgnoringCase("debt-to-income");
        assertThat(recs.get(1)).containsIgnoringCase("utilization");
    }

    @Test
    void recommendations_cust3SavingsOnly() {
        stubCust3Empty();
        List<String> recs = service.getHealth("CUST-000003").recommendations();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0)).containsIgnoringCase("savings");
    }

    @Test
    void recommendations_fallbackWhenNoneFire() {
        // Strong savings, low DTI, low utilization → no rule fires → single fallback entry (rule 4).
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(customer("CUST-000001", GBP)));
        when(accountRepository.findByCustomerId("CUST-000001")).thenReturn(List.of(account("ACC-1001", "CUST-000001", GBP)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000001", null, null)).thenReturn(List.of(
                creditTxn("TXN-5002", "CUST-000001", "5000.00", GBP),
                debitTxn("TXN-5001", "CUST-000001", "-100.00", GBP)));
        when(creditStatusRepository.findByCustomerId("CUST-000001")).thenReturn(Optional.of(
                creditStatus("CUST-000001", "5.00", "100.00", GBP)));
        List<String> recs = service.getHealth("CUST-000001").recommendations();
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0)).containsIgnoringCase("strong");
    }

    @Test
    void recommendations_capAtThree() {
        // Fire all three rules: high DTI, low savings, high utilization → list capped at 3, ordered.
        when(customerRepository.findById("CUST-000002")).thenReturn(Optional.of(customer("CUST-000002", USD)));
        when(accountRepository.findByCustomerId("CUST-000002")).thenReturn(List.of(account("ACC-2001", "CUST-000002", USD)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000002", null, null)).thenReturn(List.of(
                creditTxn("TXN-6002", "CUST-000002", "1000.00", USD),
                debitTxn("TXN-6001", "CUST-000002", "-950.00", USD)));
        when(creditStatusRepository.findByCustomerId("CUST-000002")).thenReturn(Optional.of(
                creditStatus("CUST-000002", "90.00", "900.00", USD)));
        List<String> recs = service.getHealth("CUST-000002").recommendations();
        assertThat(recs).hasSizeLessThanOrEqualTo(3);
        assertThat(recs.get(0)).containsIgnoringCase("debt-to-income");
    }

    // ---- BR-9 disclaimer (AC-19) -----------------------------------------------------------------

    @Test
    void disclaimerIsSyntheticConstant() {
        stubCust1();
        assertThat(service.getHealth("CUST-000001").disclaimer()).isEqualTo(DtoMapper.SYNTHETIC_DISCLAIMER);
    }

    // ---- BR-10 empty data (AC-15) ----------------------------------------------------------------

    @Test
    void emptyDataCustomerReturnsNeutralValues() {
        stubCust3Empty();
        FinancialHealthResponse h = service.getHealth("CUST-000003");
        assertThat(h.monthlyIncome()).isEqualByComparingTo("0.00");
        assertThat(h.monthlyExpenses()).isEqualByComparingTo("0.00");
        assertThat(h.savingsRate()).isEqualByComparingTo("0.0000");
        assertThat(h.debtToIncomeRatio()).isEqualByComparingTo("0.0000");
        assertThat(h.financialHealthScore()).isEqualTo(50);
        assertThat(h.rating()).isEqualTo("FAIR");
    }

    // ---- determinism (AC-18) ---------------------------------------------------------------------

    @Test
    void twoCallsAreDeterministic() {
        stubCust1();
        FinancialHealthResponse a = service.getHealth("CUST-000001");
        FinancialHealthResponse b = service.getHealth("CUST-000001");
        assertThat(a.monthlyIncome()).isEqualByComparingTo(b.monthlyIncome());
        assertThat(a.savingsRate()).isEqualByComparingTo(b.savingsRate());
        assertThat(a.debtToIncomeRatio()).isEqualByComparingTo(b.debtToIncomeRatio());
        assertThat(a.financialHealthScore()).isEqualTo(b.financialHealthScore());
        assertThat(a.rating()).isEqualTo(b.rating());
        assertThat(a.recommendations()).isEqualTo(b.recommendations());
    }

    // ---- negative paths (AC-11, AC-13) -----------------------------------------------------------

    @Test
    void unknownCustomerThrowsNotFound() {
        when(customerRepository.findById("CUST-999999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHealth("CUST-999999"))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void dependencyFailurePropagates() {
        when(customerRepository.findById("CUST-000004"))
                .thenThrow(new DependencyUnavailableException("customer-store"));
        assertThatThrownBy(() -> service.getHealth("CUST-000004"))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
