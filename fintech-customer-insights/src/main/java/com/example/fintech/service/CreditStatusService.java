package com.example.fintech.service;

import com.example.fintech.domain.CreditStatus;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.repository.CreditStatusRepository;
import com.example.fintech.repository.CustomerRepository;
import org.springframework.stereotype.Service;

@Service
public class CreditStatusService {

    private final CustomerRepository customerRepository;
    private final CreditStatusRepository creditStatusRepository;

    public CreditStatusService(CustomerRepository customerRepository,
                               CreditStatusRepository creditStatusRepository) {
        this.customerRepository = customerRepository;
        this.creditStatusRepository = creditStatusRepository;
    }

    public CreditStatus getCreditStatus(String customerId) {
        if (customerRepository.findById(customerId).isEmpty()) {
            throw new CustomerNotFoundException(customerId);
        }
        return creditStatusRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }
}
