package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private AccountService accountService;

    @Test
    void createAccountForUserSetsUserDefaultsAndUppercasesCurrency() {
        User user = new User();
        user.setId(20L);
        user.setFullName("Ragul Kumar");

        when(userService.getUserById(20L)).thenReturn(user);
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> {
            Account saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        Account input = new Account();
        input.setCurrencyCode("inr");
        input.setBalance(new BigDecimal("1000.00"));

        Account result = accountService.createAccountForUser(20L, input);

        assertEquals(101L, result.getId());
        assertEquals("Ragul Kumar", result.getAccountHolderName());
        assertEquals("INR", result.getCurrencyCode());
        assertEquals(AccountStatus.ACTIVE, result.getAccountStatus());
        assertEquals(Boolean.FALSE, result.getIsBucketAccount());
    }

    @Test
    void createAccountRejectsUnsupportedCurrency() {
        Account input = new Account();
        input.setAccountHolderName("Ragul");
        input.setCurrencyCode("EUR");
        input.setBalance(BigDecimal.TEN);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> accountService.createAccount(input));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Only INR and USD accounts are supported", ex.getReason());
    }

    @Test
    void validateAccountOwnedByUserRejectsMismatch() {
        User owner = new User();
        owner.setId(5L);

        Account account = new Account();
        account.setUser(owner);

        User requested = new User();
        requested.setId(8L);
        when(userService.getUserById(8L)).thenReturn(requested);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> accountService.validateAccountOwnedByUser(account, 8L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Selected source account does not belong to the user", ex.getReason());
    }

    @Test
    void updateAccountPersistsSelectedFields() {
        Account existing = new Account();
        existing.setId(1L);
        existing.setAccountHolderName("Old");
        existing.setCurrencyCode("USD");
        existing.setBalance(new BigDecimal("50.00"));

        when(accountRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(accountRepository.save(org.mockito.ArgumentMatchers.any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Account updates = new Account();
        updates.setAccountHolderName("New Name");
        updates.setCurrencyCode("inr");
        updates.setBalance(new BigDecimal("80.00"));

        accountService.updateAccount(1L, updates);

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertEquals("New Name", captor.getValue().getAccountHolderName());
        assertEquals("INR", captor.getValue().getCurrencyCode());
        assertEquals(new BigDecimal("80.00"), captor.getValue().getBalance());
    }
}

