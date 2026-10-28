package com.example.fintech.controller;

import com.example.fintech.dto.FinancialHealthResponse;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.exception.InvalidRequestException;
import com.example.fintech.service.FinancialHealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RED-phase {@code @WebMvcTest} for the financial-health controller (spec §3, §5).
 *
 * <p>Mirrors {@code FinancialOverviewControllerTest}: mocks the service and asserts HTTP status,
 * JSON body shape, validation, and RFC 7807 problem shapes. The controller must contain no business
 * logic. Fails to resolve until {@code FinancialHealthController}/{@code FinancialHealthService}/
 * {@code FinancialHealthResponse} exist — the correct RED signal.
 */
@WebMvcTest(FinancialHealthController.class)
class FinancialHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialHealthService healthService;

    private FinancialHealthResponse sampleHealth() {
        return new FinancialHealthResponse(
                "CUST-000001",
                "GBP",
                new BigDecimal("2750.75"),
                new BigDecimal("45.20"),
                new BigDecimal("0.9836"),
                new BigDecimal("0.4362"),
                82,
                "EXCELLENT",
                List.of("Your debt-to-income ratio is elevated; reducing outstanding balances could improve your score."),
                "Synthetic data for demonstration only. Does NOT represent real creditworthiness or real banking records.");
    }

    @Test
    void returns200WithHealthBody() throws Exception {
        when(healthService.getHealth(eq("CUST-000001"), isNull(), isNull())).thenReturn(sampleHealth());
        mockMvc.perform(get("/api/v1/customers/CUST-000001/financial-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-000001"))
                .andExpect(jsonPath("$.currency").value("GBP"))
                .andExpect(jsonPath("$.financialHealthScore").value(82))
                .andExpect(jsonPath("$.rating").value("EXCELLENT"))
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    void delegatesToServiceWithoutLogic() throws Exception {
        when(healthService.getHealth(eq("CUST-000001"), isNull(), isNull())).thenReturn(sampleHealth());
        mockMvc.perform(get("/api/v1/customers/CUST-000001/financial-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value("2750.75"))
                .andExpect(jsonPath("$.savingsRate").value("0.9836"))
                .andExpect(jsonPath("$.debtToIncomeRatio").value("0.4362"));
    }

    @Test
    void onlyGetIsMapped_postNotAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/customers/CUST-000001/financial-health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void returns404ForUnknownCustomer() throws Exception {
        when(healthService.getHealth(eq("CUST-999999"), isNull(), isNull()))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));
        mockMvc.perform(get("/api/v1/customers/CUST-999999/financial-health"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returns400ForMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/BADID/financial-health"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void malformed400DoesNotEchoSubmittedValue() throws Exception {
        // ERR-2 (spec v3): the field-level `errors` detail (field + reason) must not echo the submitted
        // value. The RFC 7807 `instance` legitimately carries the request path (which contains the id),
        // so the no-echo guarantee is asserted against the field-level detail only, not the whole body.
        mockMvc.perform(get("/api/v1/customers/BADID/financial-health"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors[0].field").value("customerId"))
                .andExpect(jsonPath("$.errors[0].reason",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("BADID"))));
    }

    @Test
    void returns503ForDependencyFailure() throws Exception {
        when(healthService.getHealth(anyString(), isNull(), isNull()))
                .thenThrow(new DependencyUnavailableException("customer-store"));
        mockMvc.perform(get("/api/v1/customers/CUST-000004/financial-health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void returns400OnInvalidRequestCurrency() throws Exception {
        when(healthService.getHealth(anyString(), isNull(), isNull()))
                .thenThrow(new InvalidRequestException("customer data currency is inconsistent", "currency"));
        mockMvc.perform(get("/api/v1/customers/CUST-000001/financial-health"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void errorBodyHasNoStackTrace() throws Exception {
        when(healthService.getHealth(eq("CUST-999999"), isNull(), isNull()))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));
        mockMvc.perform(get("/api/v1/customers/CUST-999999/financial-health"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Exception"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("at com.example.fintech"))));
    }

    // ---- date-range scoping (run-gh-2; AC-1, AC-4, AC-5) -----------------------------------------

    @Test
    void getHealth_withFromTo_returns200AndForwardsBounds() throws Exception {
        // AC-1: the controller binds from/to and forwards the exact bounds to the service (no logic).
        when(healthService.getHealth(eq("CUST-000001"), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(sampleHealth());

        mockMvc.perform(get("/api/v1/customers/CUST-000001/financial-health")
                        .param("from", "2025-01-01T00:00:00Z")
                        .param("to", "2025-01-10T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.customerId").value("CUST-000001"));

        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(healthService).getHealth(eq("CUST-000001"), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2025-01-01T00:00:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(OffsetDateTime.parse("2025-01-10T00:00:00Z"));
    }

    @Test
    void malformedFrom_returns400InvalidRequestFieldFrom() throws Exception {
        // AC-5 / E2: binding fails before the service is reached → 400 problem naming the parameter.
        mockMvc.perform(get("/api/v1/customers/CUST-000001/financial-health")
                        .param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:fintech:error:invalid-request"))
                .andExpect(jsonPath("$.field").value("from"));
    }

    @Test
    void fromAfterTo_returns400InvalidRequestFieldFrom() throws Exception {
        // AC-4: service throws InvalidRequestException(field="from"); handler maps to 400 problem.
        when(healthService.getHealth(anyString(), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenThrow(new InvalidRequestException("'from' must be before or equal to 'to'", "from"));

        mockMvc.perform(get("/api/v1/customers/CUST-000001/financial-health")
                        .param("from", "2025-02-01T00:00:00Z")
                        .param("to", "2025-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:fintech:error:invalid-request"))
                .andExpect(jsonPath("$.field").value("from"));
    }
}
