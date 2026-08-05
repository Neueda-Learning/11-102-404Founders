package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.DashboardAnalyticsResponse;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import com.Project.PaymentProcessingSystem.model.PaymentStatusUpdateRequest;
import com.Project.PaymentProcessingSystem.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<Payment> getAllPayments(@RequestParam(required = false) Set<PaymentStatus> status) {
        if (status == null || status.isEmpty()) {
            return paymentService.getAllPayments();
        }
        return paymentService.getPaymentsByStatuses(status);
    }

    @GetMapping("/{id}")
    public Payment getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping("/{id}/history")
    public List<PaymentStatusAudit> getPaymentHistory(@PathVariable Long id) {
        return paymentService.getPaymentAuditTrail(id);
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        Payment createdPayment = paymentService.createPayment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdPayment);
    }

    @PatchMapping("/{id}/status")
    public Payment updatePaymentStatus(@PathVariable Long id, @RequestBody PaymentStatusUpdateRequest request) {
        return paymentService.updatePaymentStatus(id, request.getStatus(), request.getReason());
    }

    @PatchMapping("/{id}/cancel")
    public Payment cancelPayment(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return paymentService.cancelPayment(id, reason);
    }

    @GetMapping("/analytics/dashboard")
    public DashboardAnalyticsResponse getDashboardAnalytics() {
        return paymentService.getDashboardAnalytics();
    }
}

