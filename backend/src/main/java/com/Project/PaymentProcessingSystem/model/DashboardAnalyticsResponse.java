package com.Project.PaymentProcessingSystem.model;

import java.math.BigDecimal;

public class DashboardAnalyticsResponse {

    private long totalPayments;
    private long successfulPayments;
    private long failedPayments;
    private long pendingPayments;
    private BigDecimal successPercentage;
    private BigDecimal totalAmountProcessed;
    private BigDecimal averageTransactionAmount;
    private BigDecimal totalBalance;
    private BigDecimal dailyTransactionLimit;
    private BigDecimal spentToday;
    private BigDecimal remainingDailyLimit;
    private BigDecimal income;
    private BigDecimal expense;
    private BigDecimal largestTransaction;
    private BigDecimal crowdfundingDonations;

    public long getTotalPayments() {
        return totalPayments;
    }

    public void setTotalPayments(long totalPayments) {
        this.totalPayments = totalPayments;
    }

    public long getSuccessfulPayments() {
        return successfulPayments;
    }

    public void setSuccessfulPayments(long successfulPayments) {
        this.successfulPayments = successfulPayments;
    }

    public long getFailedPayments() {
        return failedPayments;
    }

    public void setFailedPayments(long failedPayments) {
        this.failedPayments = failedPayments;
    }

    public long getPendingPayments() {
        return pendingPayments;
    }

    public void setPendingPayments(long pendingPayments) {
        this.pendingPayments = pendingPayments;
    }

    public BigDecimal getSuccessPercentage() {
        return successPercentage;
    }

    public void setSuccessPercentage(BigDecimal successPercentage) {
        this.successPercentage = successPercentage;
    }

    public BigDecimal getTotalAmountProcessed() {
        return totalAmountProcessed;
    }

    public void setTotalAmountProcessed(BigDecimal totalAmountProcessed) {
        this.totalAmountProcessed = totalAmountProcessed;
    }

    public BigDecimal getAverageTransactionAmount() {
        return averageTransactionAmount;
    }

    public void setAverageTransactionAmount(BigDecimal averageTransactionAmount) {
        this.averageTransactionAmount = averageTransactionAmount;
    }

    public BigDecimal getTotalBalance() {
        return totalBalance;
    }

    public void setTotalBalance(BigDecimal totalBalance) {
        this.totalBalance = totalBalance;
    }

    public BigDecimal getDailyTransactionLimit() {
        return dailyTransactionLimit;
    }

    public void setDailyTransactionLimit(BigDecimal dailyTransactionLimit) {
        this.dailyTransactionLimit = dailyTransactionLimit;
    }

    public BigDecimal getSpentToday() {
        return spentToday;
    }

    public void setSpentToday(BigDecimal spentToday) {
        this.spentToday = spentToday;
    }

    public BigDecimal getRemainingDailyLimit() {
        return remainingDailyLimit;
    }

    public void setRemainingDailyLimit(BigDecimal remainingDailyLimit) {
        this.remainingDailyLimit = remainingDailyLimit;
    }

    public BigDecimal getIncome() {
        return income;
    }

    public void setIncome(BigDecimal income) {
        this.income = income;
    }

    public BigDecimal getExpense() {
        return expense;
    }

    public void setExpense(BigDecimal expense) {
        this.expense = expense;
    }

    public BigDecimal getLargestTransaction() {
        return largestTransaction;
    }

    public void setLargestTransaction(BigDecimal largestTransaction) {
        this.largestTransaction = largestTransaction;
    }

    public BigDecimal getCrowdfundingDonations() {
        return crowdfundingDonations;
    }

    public void setCrowdfundingDonations(BigDecimal crowdfundingDonations) {
        this.crowdfundingDonations = crowdfundingDonations;
    }
}

