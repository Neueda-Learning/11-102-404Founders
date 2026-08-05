package com.Project.PaymentProcessingSystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_status_audit")
public class PaymentStatusAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 50)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 50)
    private PaymentStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private PaymentStatus status;

    @Column(name = "description")
    private String description;

    @Column(name = "changed_at")
    private LocalDateTime changedAt;

    public PaymentStatusAudit() {}

    public PaymentStatusAudit(Long id, Long paymentId, PaymentStatus fromStatus, PaymentStatus toStatus, LocalDateTime changedAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public PaymentStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(PaymentStatus fromStatus) { this.fromStatus = fromStatus; }

    public PaymentStatus getToStatus() { return toStatus; }
    public void setToStatus(PaymentStatus toStatus) { this.toStatus = toStatus; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
