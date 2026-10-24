package com.example.fintech.service;

import com.example.fintech.domain.Account;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.repository.AccountRepository;
import com.example.fintech.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountService {

    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;

    public AccountService(CustomerRepository customerRepository, AccountRepository accountRepository) {
        this.customerRepository = customerRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Returns the customer's accounts. A known customer with no accounts yields an empty list (200),
     * whereas an unknown customer yields {@link CustomerNotFoundException} (404).
     */
    public List<Account> getAccounts(String customerId) {
        requireKnownCustomer(customerId);
        return accountRepository.findByCustomerId(customerId);
    }

    private void requireKnownCustomer(String customerId) {
        if (customerRepository.findById(customerId).isEmpty()) {
            throw new CustomerNotFoundException(customerId);
        }
    }
}
