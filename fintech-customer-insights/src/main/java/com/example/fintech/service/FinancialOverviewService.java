package com.example.fintech.service;

import com.example.fintech.dto.DtoMapper;
import com.example.fintech.dto.FinancialOverviewResponse;
import org.springframework.stereotype.Service;

/**
 * Aggregates the profile, accounts, transactions and credit-status views for a customer by composing
 * the existing services. No new orchestration infrastructure is introduced.
 */
@Service
public class FinancialOverviewService {

    private final CustomerProfileService profileService;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final CreditStatusService creditStatusService;

    public FinancialOverviewService(CustomerProfileService profileService,
                                    AccountService accountService,
                                    TransactionService transactionService,
                                    CreditStatusService creditStatusService) {
        this.profileService = profileService;
        this.accountService = accountService;
        this.transactionService = transactionService;
        this.creditStatusService = creditStatusService;
    }

    public FinancialOverviewResponse getOverview(String customerId) {
        var profile = DtoMapper.toProfile(profileService.getProfile(customerId));
        var accounts = DtoMapper.toAccounts(customerId, accountService.getAccounts(customerId));
        var transactions = DtoMapper.toTransactions(customerId, null, null,
                transactionService.getTransactions(customerId, null, null));
        var creditStatus = DtoMapper.toCreditStatus(creditStatusService.getCreditStatus(customerId));
        return new FinancialOverviewResponse(profile, accounts, transactions, creditStatus);
    }
}
