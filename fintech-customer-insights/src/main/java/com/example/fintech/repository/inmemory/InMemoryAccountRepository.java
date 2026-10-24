package com.example.fintech.repository.inmemory;

import com.example.fintech.domain.Account;
import com.example.fintech.fixtures.FixtureData;
import com.example.fintech.repository.AccountRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class InMemoryAccountRepository implements AccountRepository {

    private final FixtureData fixtures;
    private final DependencyFailureSeam failureSeam;

    public InMemoryAccountRepository(FixtureData fixtures, DependencyFailureSeam failureSeam) {
        this.fixtures = fixtures;
        this.failureSeam = failureSeam;
    }

    @Override
    public List<Account> findByCustomerId(String customerId) {
        failureSeam.checkAvailability(customerId, "account-store");
        return fixtures.accountsByCustomer().getOrDefault(customerId, List.of());
    }
}
