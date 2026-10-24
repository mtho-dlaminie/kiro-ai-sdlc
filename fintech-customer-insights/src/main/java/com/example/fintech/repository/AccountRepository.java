package com.example.fintech.repository;

import com.example.fintech.domain.Account;

import java.util.List;

public interface AccountRepository {

    List<Account> findByCustomerId(String customerId);
}
