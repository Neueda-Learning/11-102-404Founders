package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User createUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User payload is required");
        }
        if (user.getFullName() == null || user.getFullName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User full name is required");
        }
        if (user.getEmail() == null || user.getEmail().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User email is required");
        }
        if (user.getDefaultCurrency() == null || user.getDefaultCurrency().trim().isEmpty()) {
            user.setDefaultCurrency("USD");
        }
        String normalizedCurrency = user.getDefaultCurrency().trim().toUpperCase(Locale.ROOT);
        if (!("USD".equals(normalizedCurrency) || "INR".equals(normalizedCurrency)
                || "EUR".equals(normalizedCurrency) || "GBP".equals(normalizedCurrency))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported default currency");
        }
        user.setDefaultCurrency(normalizedCurrency);

        if (user.getDailyTransactionLimit() == null) {
            user.setDailyTransactionLimit(new BigDecimal("5000.00"));
        }
        if (user.getDailyTransactionLimit().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Daily transaction limit must be greater than zero");
        }

        userRepository.findByEmail(user.getEmail().trim())
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
                });

        user.setId(null);
        user.setEmail(user.getEmail().trim().toLowerCase());
        return userRepository.save(user);
    }
}

