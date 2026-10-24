package com.example.fintech.service;

import com.example.fintech.domain.Customer;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.repository.CustomerRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomerProfileService {

    private final CustomerRepository customerRepository;

    public CustomerProfileService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer getProfile(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
