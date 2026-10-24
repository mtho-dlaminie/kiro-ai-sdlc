package com.example.fintech.repository;

import com.example.fintech.domain.Customer;

import java.util.Optional;

public interface CustomerRepository {

    Optional<Customer> findById(String customerId);
}
