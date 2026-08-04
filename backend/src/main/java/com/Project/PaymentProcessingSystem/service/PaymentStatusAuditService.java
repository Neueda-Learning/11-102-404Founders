package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import com.Project.PaymentProcessingSystem.repository.PaymentStatusAuditRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentStatusAuditService {

    private final PaymentStatusAuditRepository paymentStatusAuditRepository;

    public PaymentStatusAuditService(PaymentStatusAuditRepository paymentStatusAuditRepository) {
        this.paymentStatusAuditRepository = paymentStatusAuditRepository;
    }

    public List<PaymentStatusAudit> getAllAudits() {
        return paymentStatusAuditRepository.findAll();
    }

    public List<PaymentStatusAudit> getByPaymentId(Long paymentId) {
        return paymentStatusAuditRepository.findByPaymentIdOrderByChangedAtAsc(paymentId);
    }
}

