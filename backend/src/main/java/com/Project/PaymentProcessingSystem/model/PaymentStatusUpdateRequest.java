package com.Project.PaymentProcessingSystem.model;

public class PaymentStatusUpdateRequest {

    private PaymentStatus status;
    private String reason;

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}

