package com.example.fintech.service;

import com.example.fintech.domain.Account;
import com.example.fintech.domain.CreditStatus;
import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Transaction;
import com.example.fintech.domain.TransactionType;
import com.example.fintech.dto.DtoMapper;
import com.example.fintech.dto.FinancialHealthResponse;
import com.example.fintech.exception.InvalidRequestException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

/**
 * Aggregates a customer's existing synthetic data into a deterministic financial-health view by
 * composing the four existing services. No new data source, fixture or orchestration is introduced.
 *
 * <p>All arithmetic uses {@link BigDecimal} with {@link RoundingMode#HALF_EVEN}; rounding is applied
 * to final outputs only, intermediate sums/products stay at full precision (spec §4). No clock or
 * randomness is read, so results are fully deterministic. All data is synthetic; the score and rating
 * are illustrative and never a real credit assessment.
 */
@Service
public class FinancialHealthService {

    private static final int SCALE_MONEY = 2;
    private static final int SCALE_RATIO = 4;
    private static final int SCALE_WORKING = 10;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal DTI_MAX = new BigDecimal("9.9999");

    private static final BigDecimal W_SAVINGS = new BigDecimal("50");
    private static final BigDecimal W_DEBT = new BigDecimal("30");
    private static final BigDecimal W_UTIL = new BigDecimal("20");

    private static final BigDecimal REC_DTI_THRESHOLD = new BigDecimal("0.4000");
    private static final BigDecimal REC_SAVINGS_THRESHOLD = new BigDecimal("0.2000");
    private static final BigDecimal REC_UTIL_THRESHOLD = new BigDecimal("30.00");

    private static final String REC_DEBT =
            "Your debt-to-income ratio is elevated; reducing outstanding balances could improve your score.";
    private static final String REC_SAVINGS_LOW =
            "Your savings rate is low; setting aside a larger share of income could strengthen your position.";
    private static final String REC_UTIL_HIGH =
            "Your credit utilization is high; keeping balances below 30% of limits is generally favourable.";
    private static final String REC_FALLBACK =
            "Your savings rate is strong; consider maintaining your current contributions.";

    private final CustomerProfileService profileService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CreditStatusService creditStatusService;

    public FinancialHealthService(CustomerProfileService profileService,
                                  AccountService accountService,
                                  TransactionService transactionService,
                                  CreditStatusService creditStatusService) {
        this.profileService = profileService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.creditStatusService = creditStatusService;
    }

    /**
     * Derives the financial-health view, optionally scoped to an inclusive {@code [from, to]} window.
     * Either bound may be {@code null} (open-ended on that side); both {@code null} is the unscoped
     * full-history default. The range is threaded through the existing range-aware
     * {@link TransactionService}, which enforces {@code from <= to}; all scoring (BR-1..BR-8) is applied
     * unchanged to the scoped transaction subset. Accounts and credit status are point-in-time balances
     * and are not date-scoped.
     */
    public FinancialHealthResponse getHealth(String customerId, OffsetDateTime from, OffsetDateTime to) {
        Customer profile = profileService.getProfile(customerId);
        Currency profileCurrency = profile.currency();

        List<Account> accounts = accountService.getAccounts(customerId);
        List<Transaction> transactions = transactionService.getTransactions(customerId, from, to);
        CreditStatus credit = creditStatusService.getCreditStatus(customerId);

        assertSingleCurrency(profileCurrency, accounts, transactions, credit);

        BigDecimal monthlyIncome = sumByType(transactions, TransactionType.CREDIT)
                .setScale(SCALE_MONEY, ROUNDING);
        BigDecimal monthlyExpenses = sumByType(transactions, TransactionType.DEBIT).abs()
                .setScale(SCALE_MONEY, ROUNDING);

        BigDecimal savingsRateRaw = savingsRateRaw(monthlyIncome, monthlyExpenses);
        BigDecimal dtiRaw = debtToIncomeRaw(monthlyIncome, credit.outstandingDebt());

        BigDecimal savingsRate = savingsRateRaw.setScale(SCALE_RATIO, ROUNDING);
        BigDecimal debtToIncomeRatio = dtiRaw.setScale(SCALE_RATIO, ROUNDING);

        int score = score(savingsRateRaw, dtiRaw, credit.creditUtilization());
        String rating = rating(score);
        List<String> recommendations =
                recommendations(savingsRate, debtToIncomeRatio, credit.creditUtilization());

        return new FinancialHealthResponse(
                customerId,
                profileCurrency.getCurrencyCode(),
                monthlyIncome,
                monthlyExpenses,
                savingsRate,
                debtToIncomeRatio,
                score,
                rating,
                recommendations,
                DtoMapper.SYNTHETIC_DISCLAIMER);
    }

    // ---- BR-7 single-currency invariant ----------------------------------------------------------

    private void assertSingleCurrency(Currency profileCurrency, List<Account> accounts,
                                      List<Transaction> transactions, CreditStatus credit) {
        boolean mixed = accounts.stream().anyMatch(a -> !a.currency().equals(profileCurrency))
                || transactions.stream().anyMatch(t -> !t.currency().equals(profileCurrency))
                || !credit.currency().equals(profileCurrency);
        if (mixed) {
            throw new InvalidRequestException("customer data currency is inconsistent", "currency");
        }
    }

    // ---- BR-1 / BR-2 ----------------------------------------------------------------------------

    private BigDecimal sumByType(List<Transaction> transactions, TransactionType type) {
        return transactions.stream()
                .filter(t -> t.type() == type)
                .map(Transaction::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---- BR-3 (full precision; rounded to scale 4 by caller) ------------------------------------

    private BigDecimal savingsRateRaw(BigDecimal income, BigDecimal expenses) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = income.subtract(expenses).divide(income, SCALE_WORKING, ROUNDING);
        return clamp(rate, BigDecimal.ZERO, ONE);
    }

    // ---- BR-4 (full precision; rounded to scale 4 by caller) ------------------------------------

    private BigDecimal debtToIncomeRaw(BigDecimal income, BigDecimal debt) {
        if (income.compareTo(BigDecimal.ZERO) == 0) {
            return debt.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : DTI_MAX;
        }
        BigDecimal ratio = debt.divide(income, SCALE_WORKING, ROUNDING);
        return clamp(ratio, BigDecimal.ZERO, DTI_MAX);
    }

    // ---- BR-5 score from full-precision intermediates -------------------------------------------

    private int score(BigDecimal savingsRateRaw, BigDecimal dtiRaw, BigDecimal creditUtilizationPercent) {
        BigDecimal savingsComponent = min(savingsRateRaw, ONE).multiply(W_SAVINGS);
        BigDecimal debtComponent = ONE.subtract(min(dtiRaw, ONE)).multiply(W_DEBT);
        BigDecimal utilFraction = creditUtilizationPercent.divide(HUNDRED, SCALE_WORKING, ROUNDING);
        BigDecimal utilComponent = ONE.subtract(min(utilFraction, ONE)).multiply(W_UTIL);
        BigDecimal total = savingsComponent.add(debtComponent).add(utilComponent);
        int rounded = total.setScale(0, ROUNDING).intValueExact();
        return Math.max(0, Math.min(100, rounded));
    }

    // ---- BR-6 rating bands (inclusive-lower) -----------------------------------------------------

    private String rating(int score) {
        if (score >= 80) {
            return "EXCELLENT";
        }
        if (score >= 60) {
            return "GOOD";
        }
        if (score >= 40) {
            return "FAIR";
        }
        return "POOR";
    }

    // ---- BR-8 recommendations (ordered, cap 3, fallback) ----------------------------------------

    private List<String> recommendations(BigDecimal savingsRate, BigDecimal debtToIncomeRatio,
                                          BigDecimal creditUtilizationPercent) {
        List<String> recs = new ArrayList<>();
        if (debtToIncomeRatio.compareTo(REC_DTI_THRESHOLD) > 0) {
            recs.add(REC_DEBT);
        }
        if (recs.size() < 3 && savingsRate.compareTo(REC_SAVINGS_THRESHOLD) < 0) {
            recs.add(REC_SAVINGS_LOW);
        }
        if (recs.size() < 3 && creditUtilizationPercent.compareTo(REC_UTIL_THRESHOLD) > 0) {
            recs.add(REC_UTIL_HIGH);
        }
        if (recs.isEmpty()) {
            recs.add(REC_FALLBACK);
        }
        return List.copyOf(recs);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private BigDecimal clamp(BigDecimal value, BigDecimal low, BigDecimal high) {
        return value.max(low).min(high);
    }

    private BigDecimal min(BigDecimal a, BigDecimal b) {
        return a.min(b);
    }
}
