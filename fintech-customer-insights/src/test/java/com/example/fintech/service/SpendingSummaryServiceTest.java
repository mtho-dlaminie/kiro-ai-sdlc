package com.example.fintech.service;

import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Segment;
import com.example.fintech.domain.Transaction;
import com.example.fintech.domain.TransactionType;
import com.example.fintech.dto.CategorySpend;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Service unit tests (layer U) for the monthly spending-summary aggregation. Pure logic with mocked
 * repositories; asserts BR-2..BR-11 and the negative paths. Monetary assertions use
 * {@code isEqualByComparingTo} / explicit scale, never {@code equals} on unscaled values.
 *
 * <p>RED phase: references {@code SpendingSummaryService}, its {@code Result} carrier, and
 * {@code CategorySpend}, which do not exist yet — compilation fails for the right reason (missing
 * functionality named by the approved spec/design).
 */
@ExtendWith(MockitoExtension.class)
class SpendingSummaryServiceTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private SpendingSummaryService service;

    private Customer knownCustomer(String id, Currency currency) {
        return new Customer(id, "Alan", "alan@example.com", LocalDate.of(1990, 6, 23),
                Segment.PREMIER, currency);
    }

    private Transaction debit(String id, String amount, String category, OffsetDateTime date) {
        return new Transaction(id, "ACC-2001", "CUST-000002", new BigDecimal(amount), USD,
                TransactionType.DEBIT, category, date, "debit");
    }

    private Transaction credit(String id, String amount, String category, OffsetDateTime date) {
        return new Transaction(id, "ACC-2001", "CUST-000002", new BigDecimal(amount), USD,
                TransactionType.CREDIT, category, date, "credit");
    }

    private OffsetDateTime utc(int y, int m, int d) {
        return OffsetDateTime.of(y, m, d, 0, 0, 0, 0, ZoneOffset.UTC);
    }

    private void stubKnown(String id, Currency currency) {
        when(customerRepository.findById(id)).thenReturn(Optional.of(knownCustomer(id, currency)));
    }

    private void stubTxns(String id, List<Transaction> txns) {
        when(transactionRepository.findByCustomerIdAndDateRange(id, null, null)).thenReturn(txns);
    }

    // U1 — AC-2
    @Test
    void singleDebitContributesPositiveMagnitude() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(debit("TXN-1", "-45.20", "GROCERIES", utc(2025, 1, 5))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories()).hasSize(1);
        CategorySpend only = result.categories().getFirst();
        assertThat(only.category()).isEqualTo("GROCERIES");
        assertThat(only.totalSpent()).isEqualByComparingTo("45.20");
        assertThat(result.grandTotal()).isEqualByComparingTo("45.20");
        assertThat(result.debitTransactionCount()).isEqualTo(1);
    }

    // U2 — AC-3
    @Test
    void twoDebitsSameCategorySumIntoOneEntry() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(
                debit("TXN-1", "-120.00", "GROCERIES", utc(2025, 2, 10)),
                debit("TXN-2", "-80.00", "GROCERIES", utc(2025, 2, 20))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories()).hasSize(1);
        assertThat(result.categories().getFirst().totalSpent()).isEqualByComparingTo("200.00");
        assertThat(result.debitTransactionCount()).isEqualTo(2);
    }

    // U3 — AC-3
    @Test
    void debitsInDistinctCategoriesProduceOneEntryEach() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(
                debit("TXN-1", "-120.00", "GROCERIES", utc(2025, 2, 10)),
                debit("TXN-2", "-200.00", "TRANSPORT", utc(2025, 2, 5))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories()).extracting(CategorySpend::category)
                .containsExactlyInAnyOrder("GROCERIES", "TRANSPORT");
        assertThat(result.categories()).hasSize(2);
    }

    // U4 — AC-3
    @Test
    void creditOnlyCategoryIsExcluded() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(
                debit("TXN-1", "-120.00", "GROCERIES", utc(2025, 2, 10)),
                credit("TXN-2", "1800.00", "SALARY", utc(2025, 2, 1))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories()).extracting(CategorySpend::category).containsExactly("GROCERIES");
        assertThat(result.categories()).extracting(CategorySpend::category).doesNotContain("SALARY");
    }

    // U5 — AC-2 / AC-7
    @Test
    void onlyCreditsYieldsEmptyShape() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(credit("TXN-1", "1800.00", "SALARY", utc(2025, 2, 1))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories()).isEmpty();
        assertThat(result.grandTotal()).isEqualByComparingTo("0.00");
        assertThat(result.topCategory()).isNull();
        assertThat(result.debitTransactionCount()).isZero();
    }

    // AC-7 — debit customer queried over an empty range yields the empty shape
    @Test
    void emptyRangeYieldsEmptyShape() {
        OffsetDateTime from = utc(2024, 1, 1);
        OffsetDateTime to = utc(2024, 12, 31);
        when(customerRepository.findById("CUST-000002"))
                .thenReturn(Optional.of(knownCustomer("CUST-000002", USD)));
        // Repo honours the range and returns nothing for this window.
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000002", from, to))
                .thenReturn(List.of());

        SpendingSummaryService.Result result = service.summarize("CUST-000002", from, to);

        assertThat(result.categories()).isEmpty();
        assertThat(result.grandTotal()).isEqualByComparingTo("0.00");
        assertThat(result.topCategory()).isNull();
        assertThat(result.debitTransactionCount()).isZero();
    }

    // U6 — AC-7
    @Test
    void noTransactionsYieldsEmptyShape() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of());

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories()).isEmpty();
        assertThat(result.grandTotal()).isEqualByComparingTo("0.00");
        assertThat(result.topCategory()).isNull();
        assertThat(result.debitTransactionCount()).isZero();
    }

    // U7 — AC-2 / AC-9
    @Test
    void categoryTotalsHaveScaleTwo() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(debit("TXN-1", "-45.2", "GROCERIES", utc(2025, 1, 5))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories().getFirst().totalSpent().scale()).isEqualTo(2);
        assertThat(result.grandTotal().scale()).isEqualTo(2);
    }

    // U8 — AC-5 (ordering + tie by name asc)
    @Test
    void orderingDescByTotalThenNameAscOnTie() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(
                debit("TXN-1", "-850.00", "ELECTRONICS", utc(2025, 2, 14)),
                debit("TXN-2", "-120.00", "GROCERIES", utc(2025, 2, 10)),
                debit("TXN-3", "-80.00", "GROCERIES", utc(2025, 2, 20)),
                debit("TXN-4", "-200.00", "TRANSPORT", utc(2025, 2, 5))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.categories()).extracting(CategorySpend::category)
                .containsExactly("ELECTRONICS", "GROCERIES", "TRANSPORT");
        assertThat(result.grandTotal()).isEqualByComparingTo("1250.00");
        assertThat(result.topCategory()).isEqualTo("ELECTRONICS");
        assertThat(result.debitTransactionCount()).isEqualTo(4);
    }

    // U9 — AC-5 (determinism)
    @Test
    void repeatedCallsAreIdentical() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(
                debit("TXN-1", "-850.00", "ELECTRONICS", utc(2025, 2, 14)),
                debit("TXN-2", "-200.00", "TRANSPORT", utc(2025, 2, 5))));

        SpendingSummaryService.Result first = service.summarize("CUST-000002", null, null);
        SpendingSummaryService.Result second = service.summarize("CUST-000002", null, null);

        assertThat(first.categories()).extracting(CategorySpend::category)
                .isEqualTo(second.categories().stream().map(CategorySpend::category).toList());
        assertThat(first.grandTotal()).isEqualByComparingTo(second.grandTotal());
        assertThat(first.topCategory()).isEqualTo(second.topCategory());
        assertThat(first.debitTransactionCount()).isEqualTo(second.debitTransactionCount());
    }

    // U10 — AC-5 (top category highest else null)
    @Test
    void topCategoryIsHighestTotalElseNull() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(
                debit("TXN-1", "-850.00", "ELECTRONICS", utc(2025, 2, 14)),
                debit("TXN-2", "-200.00", "TRANSPORT", utc(2025, 2, 5))));

        assertThat(service.summarize("CUST-000002", null, null).topCategory()).isEqualTo("ELECTRONICS");

        stubTxns("CUST-000002", List.of(credit("TXN-9", "100.00", "SALARY", utc(2025, 2, 1))));
        assertThat(service.summarize("CUST-000002", null, null).topCategory()).isNull();
    }

    // U11 — AC-4 (count counts transactions, not categories)
    @Test
    void debitTransactionCountCountsTransactionsNotCategories() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(
                debit("TXN-1", "-120.00", "GROCERIES", utc(2025, 2, 10)),
                debit("TXN-2", "-80.00", "GROCERIES", utc(2025, 2, 20)),
                debit("TXN-3", "-200.00", "TRANSPORT", utc(2025, 2, 5))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", null, null);

        assertThat(result.debitTransactionCount()).isEqualTo(3);
        assertThat(result.categories()).hasSize(2);
    }

    // U12 — AC-4 (currency echoes customer ISO code)
    @Test
    void currencyEchoesCustomerIsoCode() {
        stubKnown("CUST-000002", USD);
        stubTxns("CUST-000002", List.of(debit("TXN-1", "-10.00", "GROCERIES", utc(2025, 1, 5))));

        assertThat(service.summarize("CUST-000002", null, null).currency()).isEqualTo("USD");
    }

    // U13 — AC-6 (inclusive bounds: service aggregates exactly what the repo returns)
    @Test
    void inclusiveDateBoundsIncludeBoundaryTxns() {
        OffsetDateTime from = utc(2025, 2, 5);
        OffsetDateTime to = utc(2025, 2, 20);
        when(customerRepository.findById("CUST-000002"))
                .thenReturn(Optional.of(knownCustomer("CUST-000002", USD)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000002", from, to)).thenReturn(List.of(
                debit("TXN-ON-FROM", "-200.00", "TRANSPORT", from),
                debit("TXN-ON-TO", "-80.00", "GROCERIES", to)));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", from, to);

        assertThat(result.debitTransactionCount()).isEqualTo(2);
        assertThat(result.categories()).extracting(CategorySpend::category)
                .containsExactlyInAnyOrder("TRANSPORT", "GROCERIES");
    }

    // U14 — AC-6 (out-of-range debit absent from totals/count)
    @Test
    void outOfRangeDebitExcludedFromTotalsAndCount() {
        OffsetDateTime from = utc(2025, 2, 1);
        OffsetDateTime to = utc(2025, 2, 28);
        when(customerRepository.findById("CUST-000002"))
                .thenReturn(Optional.of(knownCustomer("CUST-000002", USD)));
        when(transactionRepository.findByCustomerIdAndDateRange("CUST-000002", from, to)).thenReturn(List.of(
                debit("TXN-IN", "-120.00", "GROCERIES", utc(2025, 2, 10))));

        SpendingSummaryService.Result result = service.summarize("CUST-000002", from, to);

        assertThat(result.debitTransactionCount()).isEqualTo(1);
        assertThat(result.grandTotal()).isEqualByComparingTo("120.00");
    }

    // U15 — AC-6 / AC-8 (from after to → InvalidRequestException before repository)
    @Test
    void fromAfterToThrowsInvalidRequestBeforeRepository() {
        OffsetDateTime from = utc(2025, 3, 1);
        OffsetDateTime to = utc(2025, 1, 1);
        lenient().when(customerRepository.findById("CUST-000002"))
                .thenReturn(Optional.of(knownCustomer("CUST-000002", USD)));

        assertThatThrownBy(() -> service.summarize("CUST-000002", from, to))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> assertThat(((InvalidRequestException) ex).field()).isEqualTo("from"));

        verifyNoInteractions(transactionRepository);
    }

    // U16 — AC-8 (unknown customer → not found)
    @Test
    void unknownCustomerThrowsNotFound() {
        when(customerRepository.findById("CUST-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summarize("CUST-999999", null, null))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    // U17 — AC-8 (dependency failure propagates; aggregation not reached)
    @Test
    void dependencyFailurePropagatesUnavailable() {
        when(customerRepository.findById("CUST-000004"))
                .thenThrow(new DependencyUnavailableException("customer-store"));

        assertThatThrownBy(() -> service.summarize("CUST-000004", null, null))
                .isInstanceOf(DependencyUnavailableException.class);

        verifyNoInteractions(transactionRepository);
    }
}
