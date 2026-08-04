package com.Project.PaymentProcessingSystem.model;

import java.math.BigDecimal;

public class CreatePaymentRequest {

    private Long sourceAccountId;
    private Long destinationAccountId;
    private BigDecimal amount;
    private String currencyCode;
    private PaymentType paymentType;
    private Long crowdfundingCampaignId;

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
}

