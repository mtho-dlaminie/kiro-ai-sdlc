package com.example.fintech.fixtures;

import com.example.fintech.domain.Account;
import com.example.fintech.domain.AccountStatus;
import com.example.fintech.domain.AccountType;
import com.example.fintech.domain.CreditStatus;
import com.example.fintech.domain.Customer;
import com.example.fintech.domain.PaymentStatus;
import com.example.fintech.domain.Segment;
import com.example.fintech.domain.Transaction;
import com.example.fintech.domain.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Deterministic synthetic seed data for the baseline. No randomness, no clock reads.
 *
 * <p>All monetary values are {@link BigDecimal} with scale 2 and {@link RoundingMode#HALF_EVEN},
 * carrying an explicit ISO 4217 {@link Currency}. This is synthetic data only and does NOT
 * represent real creditworthiness or real banking records.
 */
@Component
public class FixtureData {

    /** Reserved well-formed id that is never present → used to exercise 404 paths. */
    public static final String UNKNOWN_CUSTOMER_ID = "CUST-999999";

    /** Reserved id that triggers a simulated dependency failure in the test seam. */
    public static final String DEPENDENCY_FAILURE_CUSTOMER_ID = "CUST-000004";

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private final Map<String, Customer> customers;
    private final Map<String, List<Account>> accountsByCustomer;
    private final Map<String, List<Transaction>> transactionsByCustomer;
    private final Map<String, CreditStatus> creditStatusByCustomer;

    public FixtureData() {
        Customer ada = new Customer("CUST-000001", "Ada Lovelace", "ada.lovelace@example.com",
                LocalDate.of(1985, 12, 10), Segment.RETAIL, GBP);
        Customer alan = new Customer("CUST-000002", "Alan Turing", "alan.turing@example.com",
                LocalDate.of(1990, 6, 23), Segment.PREMIER, USD);
        Customer grace = new Customer("CUST-000003", "Grace Hopper", "grace.hopper@example.com",
                LocalDate.of(1979, 3, 15), Segment.RETAIL, EUR);

        this.customers = Map.of(
                ada.customerId(), ada,
                alan.customerId(), alan,
                grace.customerId(), grace);

        this.accountsByCustomer = Map.of(
                ada.customerId(), List.of(
                        new Account("ACC-1001", ada.customerId(), AccountType.CHECKING, GBP,
                                money("1500.00"), money("1600.00"), AccountStatus.ACTIVE),
                        new Account("ACC-1002", ada.customerId(), AccountType.SAVINGS, GBP,
                                money("10250.75"), money("10250.75"), AccountStatus.ACTIVE)),
                alan.customerId(), List.of(
                        new Account("ACC-2001", alan.customerId(), AccountType.CHECKING, USD,
                                money("320.10"), money("320.10"), AccountStatus.ACTIVE),
                        new Account("ACC-2002", alan.customerId(), AccountType.CREDIT, USD,
                                money("4000.00"), money("-850.00"), AccountStatus.ACTIVE)),
                grace.customerId(), List.of());

        this.transactionsByCustomer = Map.of(
                ada.customerId(), List.of(
                        new Transaction("TXN-5001", "ACC-1001", ada.customerId(), money("-45.20"), GBP,
                                TransactionType.DEBIT, "GROCERIES", utc(2025, 1, 5), "Supermarket"),
                        new Transaction("TXN-5002", "ACC-1001", ada.customerId(), money("2500.00"), GBP,
                                TransactionType.CREDIT, "SALARY", utc(2025, 1, 1), "Payroll"),
                        new Transaction("TXN-5003", "ACC-1002", ada.customerId(), money("250.75"), GBP,
                                TransactionType.CREDIT, "INTEREST", utc(2025, 1, 31), "Monthly interest")),
                alan.customerId(), List.of(
                        new Transaction("TXN-6001", "ACC-2002", alan.customerId(), money("-850.00"), USD,
                                TransactionType.DEBIT, "ELECTRONICS", utc(2025, 2, 14), "Laptop"),
                        new Transaction("TXN-6002", "ACC-2001", alan.customerId(), money("1800.00"), USD,
                                TransactionType.CREDIT, "SALARY", utc(2025, 2, 1), "Payroll")),
                grace.customerId(), List.of());

        this.creditStatusByCustomer = Map.of(
                ada.customerId(), new CreditStatus(ada.customerId(), 742, money("18.50"), money("1200.00"),
                        GBP, PaymentStatus.CURRENT, rate("0.98"), 2),
                alan.customerId(), new CreditStatus(alan.customerId(), 681, money("42.75"), money("850.00"),
                        USD, PaymentStatus.LATE, rate("0.90"), 2),
                grace.customerId(), new CreditStatus(grace.customerId(), 705, money("0.00"), money("0.00"),
                        EUR, PaymentStatus.CURRENT, rate("1.00"), 0));
    }

    public Map<String, Customer> customers() {
        return customers;
    }

    public Map<String, List<Account>> accountsByCustomer() {
        return accountsByCustomer;
    }

    public Map<String, List<Transaction>> transactionsByCustomer() {
        return transactionsByCustomer;
    }

    public Map<String, CreditStatus> creditStatusByCustomer() {
        return creditStatusByCustomer;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal rate(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_EVEN);
    }

    private static OffsetDateTime utc(int year, int month, int day) {
        return OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC);
    }
}
