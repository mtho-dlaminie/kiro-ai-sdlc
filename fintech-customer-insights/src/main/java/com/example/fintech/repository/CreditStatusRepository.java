package com.example.fintech.repository;

import com.example.fintech.domain.CreditStatus;

import java.util.Optional;

public interface CreditStatusRepository {

    Optional<CreditStatus> findByCustomerId(String customerId);
}
