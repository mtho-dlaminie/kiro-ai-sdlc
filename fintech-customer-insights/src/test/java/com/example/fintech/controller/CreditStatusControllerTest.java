package com.example.fintech.controller;

import com.example.fintech.domain.CreditStatus;
import com.example.fintech.domain.PaymentStatus;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.service.CreditStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Currency;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CreditStatusController.class)
class CreditStatusControllerTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreditStatusService creditStatusService;

    @Test
    void returns200WithCreditStatusAndDisclaimer() throws Exception {
        when(creditStatusService.getCreditStatus("CUST-000001")).thenReturn(
                new CreditStatus("CUST-000001", 742, new BigDecimal("18.50"), new BigDecimal("1200.00"),
                        GBP, PaymentStatus.CURRENT, new BigDecimal("0.98"), 2));

        mockMvc.perform(get("/api/v1/customers/CUST-000001/credit-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditScore").value(742))
                .andExpect(jsonPath("$.creditUtilization").value(18.50))
                .andExpect(jsonPath("$.outstandingDebt").value(1200.00))
                .andExpect(jsonPath("$.paymentStatus").value("CURRENT"))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    void returns404ForUnknownCustomer() throws Exception {
        when(creditStatusService.getCreditStatus("CUST-999999"))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));

        mockMvc.perform(get("/api/v1/customers/CUST-999999/credit-status"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns400ForMalformedCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/CUST-1/credit-status"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns503ForDependencyFailure() throws Exception {
        when(creditStatusService.getCreditStatus(anyString()))
                .thenThrow(new DependencyUnavailableException("credit-bureau"));

        mockMvc.perform(get("/api/v1/customers/CUST-000004/credit-status"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }
}
