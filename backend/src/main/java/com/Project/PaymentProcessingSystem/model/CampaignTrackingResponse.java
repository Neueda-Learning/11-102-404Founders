package com.Project.PaymentProcessingSystem.model;

import java.math.BigDecimal;

public class CampaignTrackingResponse {

    private Long campaignId;
    private String campaignTitle;
    private BigDecimal targetAmount;
    private BigDecimal collectedAmount;
    private BigDecimal remainingAmount;
    private BigDecimal percentageComplete;
    private long daysUntilDeadline;
    private CampaignStatus status;

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

    public BigDecimal getPercentageComplete() {
        return percentageComplete;
    }

    public void setPercentageComplete(BigDecimal percentageComplete) {
        this.percentageComplete = percentageComplete;
    }

    public long getDaysUntilDeadline() {
        return daysUntilDeadline;
    }

    public void setDaysUntilDeadline(long daysUntilDeadline) {
        this.daysUntilDeadline = daysUntilDeadline;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }
}

