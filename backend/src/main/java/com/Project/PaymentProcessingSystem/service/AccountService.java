package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserService userService;

    public AccountService(AccountRepository accountRepository, UserService userService) {
        this.accountRepository = accountRepository;
        this.userService = userService;
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public List<Account> getAccountsByUserId(Long userId) {
        userService.getUserById(userId);
        return accountRepository.findByUser_Id(userId);
    }

    public Account getAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    public Account createAccount(Account account) {
        account.setId(null); // force INSERT
        if (account.getAccountStatus() == null) account.setAccountStatus(AccountStatus.ACTIVE);
        if (account.getIsBucketAccount() == null) account.setIsBucketAccount(false);
        if (account.getMaxDailyLimit() == null) account.setMaxDailyLimit(new BigDecimal("50000.00"));
        if (account.getBalance() == null) account.setBalance(BigDecimal.ZERO);
        if (account.getAccountType() == null || account.getAccountType().trim().isEmpty()) {
            account.setAccountType(account.getIsBucketAccount() != null && account.getIsBucketAccount() ? "Bucket Account" : "Checking Account");
        }
        if (account.getUser() != null && account.getUser().getId() != null) {
            User user = userService.getUserById(account.getUser().getId());
            account.setUser(user);
            if (account.getAccountHolderName() == null || account.getAccountHolderName().trim().isEmpty()) {
                account.setAccountHolderName(user.getFullName());
            }
        }
        return accountRepository.save(account);
    }

    public Account createAccountForUser(Long userId, Account account) {
        User user = userService.getUserById(userId);
        account.setUser(user);
        if (account.getAccountHolderName() == null || account.getAccountHolderName().trim().isEmpty()) {
            account.setAccountHolderName(user.getFullName());
        }
        return createAccount(account);
    }

    public Account save(Account account) {
        return accountRepository.save(account);
    }

    public Account updateAccount(Long id, Account updated) {
        Account existing = getAccountById(id);
        if (updated.getAccountHolderName() != null) existing.setAccountHolderName(updated.getAccountHolderName());
        if (updated.getCurrencyCode() != null) existing.setCurrencyCode(updated.getCurrencyCode());
        if (updated.getBalance() != null) existing.setBalance(updated.getBalance());
        if (updated.getAccountStatus() != null) existing.setAccountStatus(updated.getAccountStatus());
        if (updated.getMaxDailyLimit() != null) existing.setMaxDailyLimit(updated.getMaxDailyLimit());
        if (updated.getIsBucketAccount() != null) existing.setIsBucketAccount(updated.getIsBucketAccount());
        if (updated.getAccountNumber() != null) existing.setAccountNumber(updated.getAccountNumber());
        if (updated.getBankName() != null) existing.setBankName(updated.getBankName());
        if (updated.getBankIfsc() != null) existing.setBankIfsc(updated.getBankIfsc());
        if (updated.getAccountType() != null) existing.setAccountType(updated.getAccountType());
        return accountRepository.save(existing);
    }

    public void validateAccountOwnedByUser(Account account, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        User user = userService.getUserById(userId);
        if (account.getUser() == null || account.getUser().getId() == null || !user.getId().equals(account.getUser().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selected source account does not belong to the user");
        }
    }

    public void deleteAccount(Long id) {
        accountRepository.delete(getAccountById(id));
    }
}
