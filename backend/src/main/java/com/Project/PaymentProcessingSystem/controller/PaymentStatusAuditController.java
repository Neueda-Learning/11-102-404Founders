package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import com.Project.PaymentProcessingSystem.service.PaymentStatusAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audits")
public class PaymentStatusAuditController {

    private final PaymentStatusAuditService paymentStatusAuditService;

    public PaymentStatusAuditController(PaymentStatusAuditService paymentStatusAuditService) {
        this.paymentStatusAuditService = paymentStatusAuditService;
    }

    @GetMapping
    public List<PaymentStatusAudit> getAllAudits() {
        return paymentStatusAuditService.getAllAudits();
    }

    @GetMapping("/payment/{paymentId}")
    public List<PaymentStatusAudit> getByPaymentId(@PathVariable Long paymentId) {
        return paymentStatusAuditService.getByPaymentId(paymentId);
    }
}

