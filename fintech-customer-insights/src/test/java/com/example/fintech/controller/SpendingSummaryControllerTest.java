package com.example.fintech.controller;

import com.example.fintech.dto.CategorySpend;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.exception.DependencyUnavailableException;
import com.example.fintech.exception.InvalidRequestException;
import com.example.fintech.service.SpendingSummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller slice tests (layer C) for the spending-summary endpoint. Mirrors
 * {@code TransactionControllerTest}: {@code @WebMvcTest} + {@code @MockitoBean} service, asserting
 * status codes, JSON bodies, validation and RFC 7807 problem shapes. The controller invokes the real
 * static {@code DtoMapper.toSpendingSummary} over the mocked service result.
 *
 * <p>RED phase: references {@code SpendingSummaryController}, {@code SpendingSummaryService} and its
 * {@code Result}, which do not exist yet — the slice fails to load for the right reason.
 */
@WebMvcTest(SpendingSummaryController.class)
class SpendingSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpendingSummaryService spendingSummaryService;

    private SpendingSummaryService.Result multiCategoryResult() {
        return new SpendingSummaryService.Result("USD",
                List.of(new CategorySpend("ELECTRONICS", new BigDecimal("850.00")),
                        new CategorySpend("GROCERIES", new BigDecimal("200.00")),
                        new CategorySpend("TRANSPORT", new BigDecimal("200.00"))),
                new BigDecimal("1250.00"), "ELECTRONICS", 4);
    }

    private SpendingSummaryService.Result emptyResult() {
        return new SpendingSummaryService.Result("EUR", List.of(),
                new BigDecimal("0.00"), null, 0);
    }

    // C1 — AC-1
    @Test
    void returns200WithSummaryBody() throws Exception {
        when(spendingSummaryService.summarize(eq("CUST-000002"), any(), any()))
                .thenReturn(multiCategoryResult());

        mockMvc.perform(get("/api/v1/customers/CUST-000002/spending-summary"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.categories[0].category").value("ELECTRONICS"))
                .andExpect(jsonPath("$.categories[0].totalSpent").value(850.00))
                .andExpect(jsonPath("$.grandTotal").value(1250.00))
                .andExpect(jsonPath("$.topCategory").value("ELECTRONICS"))
                .andExpect(jsonPath("$.debitTransactionCount").value(4));
    }

    // C2 — AC-4 / AC-9
    @Test
    void bodyContainsAllDocumentedFields() throws Exception {
        when(spendingSummaryService.summarize(eq("CUST-000002"), any(), any()))
                .thenReturn(multiCategoryResult());

        mockMvc.perform(get("/api/v1/customers/CUST-000002/spending-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-000002"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.from").doesNotExist())
                .andExpect(jsonPath("$.to").doesNotExist())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.grandTotal").exists())
                .andExpect(jsonPath("$.topCategory").value("ELECTRONICS"))
                .andExpect(jsonPath("$.debitTransactionCount").value(4))
                .andExpect(jsonPath("$.disclaimer").value(containsString("Synthetic")));
    }

    // C3 — AC-4 (omitted dates serialize as null)
    @Test
    void omittedDatesSerializeAsNullAndDisclaimerPresent() throws Exception {
        when(spendingSummaryService.summarize(eq("CUST-000002"), any(), any()))
                .thenReturn(multiCategoryResult());

        mockMvc.perform(get("/api/v1/customers/CUST-000002/spending-summary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"from\":null")))
                .andExpect(content().string(containsString("\"to\":null")))
                .andExpect(jsonPath("$.disclaimer").value(containsString("Synthetic")));
    }

    // C4 — AC-7 (empty shape, never 404)
    @Test
    void returns200EmptyShapeForKnownCustomerWithNoSpending() throws Exception {
        when(spendingSummaryService.summarize(eq("CUST-000003"), any(), any()))
                .thenReturn(emptyResult());

        mockMvc.perform(get("/api/v1/customers/CUST-000003/spending-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories").isArray())
                .andExpect(jsonPath("$.categories").isEmpty())
                .andExpect(jsonPath("$.grandTotal").value(0.00))
                .andExpect(jsonPath("$.topCategory").doesNotExist())
                .andExpect(jsonPath("$.debitTransactionCount").value(0));
    }

    // C5 — AC-8 (malformed id → 400 problem+json)
    @Test
    void returns400ForMalformedCustomerId() throws Exception {
        mockMvc.perform(get("/api/v1/customers/bad/spending-summary"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // C6 — AC-6 / AC-8 (from after to → 400, field=from)
    @Test
    void returns400WhenFromAfterTo() throws Exception {
        when(spendingSummaryService.summarize(anyString(), any(), any()))
                .thenThrow(new InvalidRequestException("'from' must be before or equal to 'to'", "from"));

        mockMvc.perform(get("/api/v1/customers/CUST-000002/spending-summary")
                        .param("from", "2025-02-01T00:00:00Z")
                        .param("to", "2025-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.field").value("from"));
    }

    // C7 — AC-8 (unparseable from → 400)
    @Test
    void returns400ForUnparseableFrom() throws Exception {
        mockMvc.perform(get("/api/v1/customers/CUST-000002/spending-summary")
                        .param("from", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // C8 — AC-8 (unknown customer → 404)
    @Test
    void returns404ForUnknownCustomer() throws Exception {
        when(spendingSummaryService.summarize(eq("CUST-999999"), any(), any()))
                .thenThrow(new CustomerNotFoundException("CUST-999999"));

        mockMvc.perform(get("/api/v1/customers/CUST-999999/spending-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // C9 — AC-8 (dependency failure → 503)
    @Test
    void returns503ForDependencyFailure() throws Exception {
        when(spendingSummaryService.summarize(eq("CUST-000004"), any(), any()))
                .thenThrow(new DependencyUnavailableException("customer-store"));

        mockMvc.perform(get("/api/v1/customers/CUST-000004/spending-summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    // C10 — AC-2 / AC-9 (money scale preserved on the wire)
    @Test
    void moneyFieldsRenderWithScaleTwo() throws Exception {
        when(spendingSummaryService.summarize(eq("CUST-000002"), any(), any()))
                .thenReturn(multiCategoryResult());

        mockMvc.perform(get("/api/v1/customers/CUST-000002/spending-summary"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"totalSpent\":850.00")))
                .andExpect(content().string(containsString("\"grandTotal\":1250.00")));
    }
}
