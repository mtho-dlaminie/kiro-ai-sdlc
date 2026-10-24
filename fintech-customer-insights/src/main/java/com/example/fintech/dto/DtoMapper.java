package com.example.fintech.dto;

import com.example.fintech.domain.Account;
import com.example.fintech.domain.CreditStatus;
import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Transaction;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pure mapping from domain models to API DTOs. No business logic, no persistence concerns.
 */
public final class DtoMapper {

    /** Synthetic-data disclaimer surfaced on credit responses. */
    public static final String SYNTHETIC_DISCLAIMER =
            "Synthetic data for demonstration only. Does NOT represent real creditworthiness or real banking records.";

    private DtoMapper() {
    }

    public static CustomerProfileResponse toProfile(Customer customer) {
        return new CustomerProfileResponse(
                customer.customerId(),
                customer.fullName(),
                customer.email(),
                customer.dateOfBirth(),
                customer.segment().name(),
                customer.currency().getCurrencyCode());
    }

    public static AccountResponse toAccount(Account account) {
        return new AccountResponse(
                account.accountId(),
                account.type().name(),
                account.currency().getCurrencyCode(),
                account.availableBalance(),
                account.currentBalance(),
                account.status().name());
    }

    public static AccountsResponse toAccounts(String customerId, List<Account> accounts) {
        List<AccountResponse> items = accounts.stream().map(DtoMapper::toAccount).toList();
        return new AccountsResponse(customerId, items.size(), items);
    }

    public static TransactionResponse toTransaction(Transaction transaction) {
        return new TransactionResponse(
                transaction.transactionId(),
                transaction.accountId(),
                transaction.amount(),
                transaction.currency().getCurrencyCode(),
                transaction.type().name(),
                transaction.category(),
                transaction.transactionDate(),
                transaction.description());
    }

    public static TransactionsResponse toTransactions(String customerId, OffsetDateTime from, OffsetDateTime to,
                                                      List<Transaction> transactions) {
        List<TransactionResponse> items = transactions.stream().map(DtoMapper::toTransaction).toList();
        return new TransactionsResponse(customerId, from, to, items.size(), items);
    }

    public static CreditStatusResponse toCreditStatus(CreditStatus creditStatus) {
        return new CreditStatusResponse(
                creditStatus.customerId(),
                creditStatus.creditScore(),
                creditStatus.creditUtilization(),
                creditStatus.outstandingDebt(),
                creditStatus.currency().getCurrencyCode(),
                creditStatus.paymentStatus().name(),
                creditStatus.onTimePaymentRate(),
                creditStatus.openAccounts(),
                SYNTHETIC_DISCLAIMER);
    }
}
