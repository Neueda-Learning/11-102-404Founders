package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    public Account getAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    public Account createAccount(Account account) {
        // Force INSERT semantics even if client sends an id in payload.
        account.setId(null);

        if (account.getAccountStatus() == null) {
            account.setAccountStatus(com.Project.PaymentProcessingSystem.model.AccountStatus.ACTIVE);
        }
        if (account.getIsBucketAccount() == null) {
            account.setIsBucketAccount(false);
        }
        if (account.getMaxDailyLimit() == null) {
            account.setMaxDailyLimit(new java.math.BigDecimal("50000.00"));
        }

        return accountRepository.save(account);
    }

    public Account updateAccount(Long id, Account updatedAccount) {
        Account existingAccount = getAccountById(id);

        existingAccount.setAccountHolderName(updatedAccount.getAccountHolderName());
        existingAccount.setCurrencyCode(updatedAccount.getCurrencyCode());
        existingAccount.setBalance(updatedAccount.getBalance());

        if (updatedAccount.getAccountStatus() != null) {
            existingAccount.setAccountStatus(updatedAccount.getAccountStatus());
        }

        return accountRepository.save(existingAccount);
    }

    public void deleteAccount(Long id) {
        Account existingAccount = getAccountById(id);
        accountRepository.delete(existingAccount);
    }
}
