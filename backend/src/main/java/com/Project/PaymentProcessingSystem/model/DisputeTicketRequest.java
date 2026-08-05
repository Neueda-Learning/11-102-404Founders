package com.Project.PaymentProcessingSystem.model;

public class DisputeTicketRequest {

    private Long paymentId;
    private Long accountId;
    private Long userId;
    private DisputeRole disputeRole;
    private String reason;
    private Boolean recoveryRequested;

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public DisputeRole getDisputeRole() {
        return disputeRole;
    }

    public void setDisputeRole(DisputeRole disputeRole) {
        this.disputeRole = disputeRole;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean getRecoveryRequested() {
        return recoveryRequested;
    }

    public void setRecoveryRequested(Boolean recoveryRequested) {
        this.recoveryRequested = recoveryRequested;
    }
}

