package com.Project.PaymentProcessingSystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(name = "account_holder_name", nullable = false)
    private String accountHolderName;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "is_bucket_account")
    private Boolean isBucketAccount = false;

    @Column(name = "max_daily_limit", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxDailyLimit = new BigDecimal("50000.00");

    public Account() {
    }

    public Account(Long id, String accountHolderName, String currencyCode, BigDecimal balance, AccountStatus accountStatus) {
        this.id = id;
        this.accountHolderName = accountHolderName;
        this.currencyCode = currencyCode;
        this.balance = balance;
        this.accountStatus = accountStatus;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public AccountStatus getAccountStatus() { return accountStatus; }
    public void setAccountStatus(AccountStatus accountStatus) { this.accountStatus = accountStatus; }

    public Boolean getIsBucketAccount() { return isBucketAccount; }
    public void setIsBucketAccount(Boolean isBucketAccount) { this.isBucketAccount = isBucketAccount; }

    public BigDecimal getMaxDailyLimit() { return maxDailyLimit; }
    public void setMaxDailyLimit(BigDecimal maxDailyLimit) { this.maxDailyLimit = maxDailyLimit; }
}
