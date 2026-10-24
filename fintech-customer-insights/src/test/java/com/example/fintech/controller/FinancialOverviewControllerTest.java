package com.example.fintech.controller;

import com.example.fintech.dto.AccountsResponse;
import com.example.fintech.dto.CreditStatusResponse;
import com.example.fintech.dto.CustomerProfileResponse;
import com.example.fintech.dto.FinancialOverviewResponse;
import com.example.fintech.dto.TransactionsResponse;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.service.FinancialOverviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialOverviewController.class)
class FinancialOverviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialOverviewService overviewService;

    private FinancialOverviewResponse sampleOverview() {
        return new FinancialOverviewResponse(
                new CustomerProfileResponse("CUST-000001", "Ada Lovelace", "ada.lovelace@example.com",
                        LocalDate.of(1985, 12, 10), "RETAIL", "GBP"),
                new AccountsResponse("CUST-000001", 0, List.of()),
                new TransactionsResponse("CUST-000001", null, null, 0, List.of()),
                new CreditStatusResponse("CUST-000001", 742, new BigDecimal("18.50"),
                        new BigDecimal("1200.00"), "GBP", "CURRENT", new BigDecimal("0.98"), 2,
                        "Synthetic data for demonstration only."));
    }

    @Test
    void returns200WithAggregatedOverview() throws Exception {
        when(overviewService.getOverview("CUST-000001")).thenReturn(sampleOverview());

        mockMvc.perform(get("/api/v1/customers/CUST-000001/financial-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.customerId").value("CUST-000001"))
                .andExpect(jsonPath("$.accounts").exists())
                .andExpect(jsonPath("$.transactions").exists())
                .andExpect(jsonPath("$.creditStatus.creditScore").value(742));
    }

    @Test
    void returns404ForUnknownCustomer() throws Exception {
        when(overviewService.getOverview("CUST-999999"))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));

        mockMvc.perform(get("/api/v1/customers/CUST-999999/financial-overview"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns400ForMalformedCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/xxx/financial-overview"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns503ForDependencyFailure() throws Exception {
        when(overviewService.getOverview(anyString()))
                .thenThrow(new DependencyUnavailableException("customer-store"));

        mockMvc.perform(get("/api/v1/customers/CUST-000004/financial-overview"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
