package com.example.fintech.service;

import com.example.fintech.domain.Account;
import com.example.fintech.domain.AccountStatus;
import com.example.fintech.domain.AccountType;
import com.example.fintech.domain.Customer;
import com.example.fintech.domain.Segment;
import com.example.fintech.exception.CustomerNotFoundException;
import com.example.fintech.repository.AccountRepository;
import com.example.fintech.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService service;

    private Customer knownCustomer(String id) {
        return new Customer(id, "Ada", "ada@example.com", LocalDate.of(1985, 12, 10), Segment.RETAIL, GBP);
    }

    @Test
    void returnsAccountsForKnownCustomer() {
        when(customerRepository.findById("CUST-000001")).thenReturn(Optional.of(knownCustomer("CUST-000001")));
        Account account = new Account("ACC-1001", "CUST-000001", AccountType.CHECKING, GBP,
                new BigDecimal("1500.00"), new BigDecimal("1600.00"), AccountStatus.ACTIVE);
        when(accountRepository.findByCustomerId("CUST-000001")).thenReturn(List.of(account));

        List<Account> result = service.getAccounts("CUST-000001");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().availableBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(result.getFirst().availableBalance().scale()).isEqualTo(2);
    }

    @Test
    void returnsEmptyListForKnownCustomerWithNoAccounts() {
        when(customerRepository.findById("CUST-000003")).thenReturn(Optional.of(knownCustomer("CUST-000003")));
        when(accountRepository.findByCustomerId("CUST-000003")).thenReturn(List.of());

        assertThat(service.getAccounts("CUST-000003")).isEmpty();
    }

    @Test
    void throwsNotFoundForUnknownCustomer() {
        when(customerRepository.findById("CUST-999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAccounts("CUST-999999"))
                .isInstanceOf(CustomerNotFoundException.class);
    }
}
