package com.example.fintech.repository.inmemory;

import com.example.fintech.domain.Customer;
import com.example.fintech.fixtures.FixtureData;
import com.example.fintech.repository.CustomerRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InMemoryCustomerRepository implements CustomerRepository {

    private final FixtureData fixtures;
    private final DependencyFailureSeam failureSeam;

    public InMemoryCustomerRepository(FixtureData fixtures, DependencyFailureSeam failureSeam) {
        this.fixtures = fixtures;
        this.failureSeam = failureSeam;
    }

    @Override
    public Optional<Customer> findById(String customerId) {
        failureSeam.checkAvailability(customerId, "customer-store");
        return Optional.ofNullable(fixtures.customers().get(customerId));
    }
}
