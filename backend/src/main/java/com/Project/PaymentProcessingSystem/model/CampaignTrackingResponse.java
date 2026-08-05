package com.Project.PaymentProcessingSystem.model;

import java.math.BigDecimal;

public class CampaignTrackingResponse {

    private Long campaignId;
    private String campaignTitle;
    private BigDecimal targetAmount;
    private BigDecimal collectedAmount;
    private BigDecimal remainingAmount;
    private long daysUntilDeadline;

    public Long getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(Long campaignId) {
        this.campaignId = campaignId;
    }

    public String getCampaignTitle() {
        return campaignTitle;
    }

    public void setCampaignTitle(String campaignTitle) {
        this.campaignTitle = campaignTitle;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public BigDecimal getCollectedAmount() {
        return collectedAmount;
    }

    public void setCollectedAmount(BigDecimal collectedAmount) {
        this.collectedAmount = collectedAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public long getDaysUntilDeadline() {
        return daysUntilDeadline;
    }

    public void setDaysUntilDeadline(long daysUntilDeadline) {
        this.daysUntilDeadline = daysUntilDeadline;
    }
}

