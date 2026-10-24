package com.example.fintech.repository.inmemory;

import com.example.fintech.domain.CreditStatus;
import com.example.fintech.fixtures.FixtureData;
import com.example.fintech.repository.CreditStatusRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InMemoryCreditStatusRepository implements CreditStatusRepository {

    private final FixtureData fixtures;
    private final DependencyFailureSeam failureSeam;

    public InMemoryCreditStatusRepository(FixtureData fixtures, DependencyFailureSeam failureSeam) {
        this.fixtures = fixtures;
        this.failureSeam = failureSeam;
    }

    @Override
    public Optional<CreditStatus> findByCustomerId(String customerId) {
        failureSeam.checkAvailability(customerId, "credit-bureau");
        return Optional.ofNullable(fixtures.creditStatusByCustomer().get(customerId));
    }
}
