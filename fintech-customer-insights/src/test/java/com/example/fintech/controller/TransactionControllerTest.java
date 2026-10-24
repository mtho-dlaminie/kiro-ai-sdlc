package com.example.fintech.controller;

import com.example.fintech.domain.Transaction;
import com.example.fintech.domain.TransactionType;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.exception.InvalidRequestException;
import com.example.fintech.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    void returns200WithTransactions() throws Exception {
        when(transactionService.getTransactions(eq("CUST-000001"), any(), any())).thenReturn(List.of(
                new Transaction("TXN-5001", "ACC-1001", "CUST-000001", new BigDecimal("-45.20"), GBP,
                        TransactionType.DEBIT, "GROCERIES",
                        OffsetDateTime.of(2025, 1, 5, 0, 0, 0, 0, ZoneOffset.UTC), "Supermarket")));

        mockMvc.perform(get("/api/v1/customers/CUST-000001/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.items[0].transactionId").value("TXN-5001"))
                .andExpect(jsonPath("$.items[0].amount").value(-45.20))
                .andExpect(jsonPath("$.items[0].type").value("DEBIT"));
    }

    @Test
    void returns200EmptyListForKnownCustomerWithNoTransactions() throws Exception {
        when(transactionService.getTransactions(eq("CUST-000003"), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/customers/CUST-000003/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void returns400WhenFromAfterTo() throws Exception {
        when(transactionService.getTransactions(anyString(), any(), any()))
                .thenThrow(new InvalidRequestException("'from' must be before or equal to 'to'", "from"));

        mockMvc.perform(get("/api/v1/customers/CUST-000001/transactions")
                        .param("from", "2025-02-01T00:00:00Z")
                        .param("to", "2025-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.field").value("from"));
    }

    @Test
    void returns404ForUnknownCustomer() throws Exception {
        when(transactionService.getTransactions(eq("CUST-999999"), any(), any()))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));

        mockMvc.perform(get("/api/v1/customers/CUST-999999/transactions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns400ForMalformedCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/bad/transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns503ForDependencyFailure() throws Exception {
        when(transactionService.getTransactions(anyString(), any(), any()))
                .thenThrow(new DependencyUnavailableException("transaction-store"));

        mockMvc.perform(get("/api/v1/customers/CUST-000004/transactions"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
