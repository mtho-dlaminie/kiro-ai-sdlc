package com.example.fintech.service;

import com.example.fintech.domain.CreditStatus;
import com.example.fintech.domain.Customer;
import com.example.fintech.domain.PaymentStatus;
import com.example.fintech.domain.Segment;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.repository.CreditStatusRepository;
import com.example.fintech.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditStatusServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CreditStatusRepository creditStatusRepository;

    @InjectMocks
    private CreditStatusService service;

    private Customer knownCustomer(String id) {
        return new Customer(id, "Ada", "ada@example.com", LocalDate.of(1985, 12, 10), Segment.RETAIL, GBP);
    }

    @Test
    void returnsCreditStatusForKnownCustomer() {
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(knownCustomer("CUST-000001")));
        CreditStatus status = new CreditStatus("CUST-000001", 742, new BigDecimal("18.50"),
                new BigDecimal("1200.00"), GBP, PaymentStatus.CURRENT, new BigDecimal("0.98"), 2);
        when(creditStatusRepository.findByCustomerId("CUST-000001")).thenReturn(Optional.of(status));

        CreditStatus result = service.getCreditStatus("CUST-000001");

        assertThat(result.creditScore()).isEqualTo(742);
        assertThat(result.creditUtilization()).isEqualByComparingTo(new BigDecimal("18.50"));
        assertThat(result.outstandingDebt().scale()).isEqualTo(2);
    }

    @Test
    void throwsNotFoundForUnknownCustomer() {
        when(customerRepository.findById("CUST-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCreditStatus("CUST-999999"))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
