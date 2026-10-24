package com.example.fintech.controller;

import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Segment;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.service.CustomerProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Currency;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerProfileController.class)
class CustomerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerProfileService profileService;

    @Test
    void returns200AndProfileBody() throws Exception {
        when(profileService.getProfile("CUST-000001")).thenReturn(new Customer("CUST-000001", "Ada Lovelace",
                "ada.lovelace@example.com", LocalDate.of(1985, 12, 10), Segment.RETAIL,
                Currency.getInstance("GBP")));

        mockMvc.perform(get("/api/v1/customers/CUST-000001/profile"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.customerId").value("CUST-000001"))
                .andExpect(jsonPath("$.fullName").value("Ada Lovelace"))
                .andExpect(jsonPath("$.currency").value("GBP"));
    }

    @Test
    void returns404ProblemJsonForUnknownCustomer() throws Exception {
        when(profileService.getProfile("CUST-999999"))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));

        mockMvc.perform(get("/api/v1/customers/CUST-999999/profile"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Customer Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.type").exists())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void returns400ForMalformedCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/bad-id/profile"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returns503ProblemJsonForDependencyFailure() throws Exception {
        when(profileService.getProfile(anyString()))
                .thenThrow(new DependencyUnavailableException("customer-store"));

        mockMvc.perform(get("/api/v1/customers/CUST-000004/profile"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Dependency Unavailable"))
                .andExpect(jsonPath("$.status").value(503));
    }
}
