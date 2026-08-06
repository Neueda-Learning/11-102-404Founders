package com.Project.PaymentProcessingSystem.model;

import java.math.BigDecimal;

public class CreatePaymentRequest {

    private Long sourceAccountId;
    private Long destinationAccountId;
    private BigDecimal amount;
    private String currencyCode;
    private String destinationCurrencyCode;
    private PaymentType paymentType;
    private Long crowdfundingCampaignId;
    private String idempotencyKey;
    private Long userId;
    private String sourceAccountNumber;
    private String destinationAccountNumber;
    private Boolean forexConfirmed;
    private Boolean confirmationTimedOut;

    public CreatePaymentRequest() {
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public void setSourceAccountId(Long sourceAccountId) {
        this.sourceAccountId = sourceAccountId;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(Long destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getDestinationCurrencyCode() {
        return destinationCurrencyCode;
    }

    public void setDestinationCurrencyCode(String destinationCurrencyCode) {
        this.destinationCurrencyCode = destinationCurrencyCode;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(PaymentType paymentType) {
        this.paymentType = paymentType;
    }

    public Long getCrowdfundingCampaignId() {
        return crowdfundingCampaignId;
    }

    public void setCrowdfundingCampaignId(Long crowdfundingCampaignId) {
        this.crowdfundingCampaignId = crowdfundingCampaignId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSourceAccountNumber() {
        return sourceAccountNumber;
    }

    public void setSourceAccountNumber(String sourceAccountNumber) {
        this.sourceAccountNumber = sourceAccountNumber;
    }

    public String getDestinationAccountNumber() {
        return destinationAccountNumber;
    }

    public void setDestinationAccountNumber(String destinationAccountNumber) {
        this.destinationAccountNumber = destinationAccountNumber;
    }

    public Boolean getForexConfirmed() {
        return forexConfirmed;
    }

    public void setForexConfirmed(Boolean forexConfirmed) {
        this.forexConfirmed = forexConfirmed;
    }

    public Boolean getConfirmationTimedOut() {
        return confirmationTimedOut;
    }

    public void setConfirmationTimedOut(Boolean confirmationTimedOut) {
        this.confirmationTimedOut = confirmationTimedOut;
    }
}

