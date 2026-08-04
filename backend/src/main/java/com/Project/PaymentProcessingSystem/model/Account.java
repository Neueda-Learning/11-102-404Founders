package com.Project.PaymentProcessingSystem.model;

import java.math.BigDecimal;

public class Account {

    private Long id;
    private String accountHolderName;
    private String currencyCode;
    private BigDecimal balance;
    private AccountStatus accountStatus;

    public Account() {
    }

    public Account(Long id, String accountHolderName, String currencyCode, BigDecimal balance, AccountStatus accountStatus) {
        this.id = id;
        this.accountHolderName = accountHolderName;
        this.currencyCode = currencyCode;
        this.balance = balance;
        this.accountStatus = accountStatus;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }
}

