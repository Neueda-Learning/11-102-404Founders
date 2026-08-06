package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createUserAppliesDefaultsAndNormalizesEmail() {
        User input = new User();
        input.setFullName("Ragul Kumar");
        input.setEmail("Ragul@Example.com ");

        when(userRepository.findByEmail("Ragul@Example.com")).thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        User result = userService.createUser(input);

        assertEquals(10L, result.getId());
        assertEquals("ragul@example.com", result.getEmail());
        assertEquals("INR", result.getDefaultCurrency());
        assertEquals(new BigDecimal("5000.00"), result.getDailyTransactionLimit());
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        User input = new User();
        input.setFullName("Ragul Kumar");
        input.setEmail("ragul@example.com");

        when(userRepository.findByEmail("ragul@example.com")).thenReturn(Optional.of(new User()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> userService.createUser(input));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("Email already exists", ex.getReason());
    }

    @Test
    void updateDailyTransactionLimitRejectsNonPositiveLimit() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.updateDailyTransactionLimit(1L, BigDecimal.ZERO));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Daily transaction limit must be greater than zero", ex.getReason());
    }

    @Test
    void updateDailyTransactionLimitPersistsValidLimit() {
        User existing = new User();
        existing.setId(1L);
        existing.setDailyTransactionLimit(new BigDecimal("5000.00"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.updateDailyTransactionLimit(1L, new BigDecimal("12000.00"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals(new BigDecimal("12000.00"), captor.getValue().getDailyTransactionLimit());
    }
}

