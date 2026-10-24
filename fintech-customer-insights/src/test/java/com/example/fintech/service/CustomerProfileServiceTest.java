package com.example.fintech.service;

import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Segment;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerProfileServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private CustomerProfileService service;

    @Test
    void returnsProfileForKnownCustomer() {
        Customer customer = new Customer("CUST-000001", "Ada Lovelace", "ada.lovelace@example.com",
                LocalDate.of(1985, 12, 10), Segment.RETAIL, Currency.getInstance("GBP"));
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(customer));

        Customer result = service.getProfile("CUST-000001");

        assertThat(result.fullName()).isEqualTo("Ada Lovelace");
        assertThat(result.email()).isEqualTo("ada.lovelace@example.com");
    }

    @Test
    void throwsNotFoundForUnknownCustomer() {
        when(customerRepository.findById("CUST-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile("CUST-999999"))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
