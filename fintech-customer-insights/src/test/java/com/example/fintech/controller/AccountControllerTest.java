package com.example.fintech.controller;

import com.example.fintech.domain.Account;
import com.example.fintech.domain.AccountStatus;
import com.example.fintech.domain.AccountType;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void returns200WithAccounts() throws Exception {
        when(accountService.getAccounts("CUST-000001")).thenReturn(List.of(
                new Account("ACC-1001", "CUST-000001", AccountType.CHECKING, GBP,
                        new BigDecimal("1500.00"), new BigDecimal("1600.00"), AccountStatus.ACTIVE)));

        mockMvc.perform(get("/api/v1/customers/CUST-000001/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-000001"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].accountId").value("ACC-1001"))
                .andExpect(jsonPath("$.items[0].availableBalance").value(1500.00))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }

    @Test
    void returns200EmptyListForKnownCustomerWithNoAccounts() throws Exception {
        when(accountService.getAccounts("CUST-000003")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customers/CUST-000003/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void returns404ForUnknownCustomer() throws Exception {
        when(accountService.getAccounts("CUST-999999"))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));

        mockMvc.perform(get("/api/v1/customers/CUST-999999/accounts"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns400ForMalformedCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/123/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns503ForDependencyFailure() throws Exception {
        when(accountService.getAccounts(anyString()))
                .thenThrow(new DependencyUnavailableException("account-store"));

        mockMvc.perform(get("/api/v1/customers/CUST-000004/accounts"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
