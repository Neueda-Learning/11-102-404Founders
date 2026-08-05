package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.DashboardAnalyticsResponse;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public List<Payment> getAllPayments(@RequestParam(required = false) Set<PaymentStatus> status,
                                        @RequestParam(required = false) Long userId,
                                        @RequestParam(required = false) PaymentType paymentType,
                                        @RequestParam(required = false) String currency,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
                                        @RequestParam(required = false) Long sourceAccountId,
                                        @RequestParam(required = false) Long destinationAccountId,
                                        @RequestParam(required = false) String reference,
                                        @RequestParam(defaultValue = "date") String sortBy,
                                        @RequestParam(defaultValue = "desc") String sortDir) {
        return paymentService.findPaymentsForWorkspace(
                userId,
                status,
                paymentType,
                currency,
                fromDate,
                toDate,
                sourceAccountId,
                destinationAccountId,
                reference,
                sortBy,
                sortDir
        );
    }

    @GetMapping("/{id}")
    public Payment getPaymentById(@PathVariable Long id) {
        return paymentService.getPaymentById(id);
    }

    @GetMapping("/{id}/history")
    public List<PaymentStatusAudit> getHistory(@PathVariable Long id) {
        return paymentService.getPaymentAuditTrail(id);
    }

    @PostMapping
    public ResponseEntity<Payment> createPayment(@RequestBody CreatePaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request));
    }

    @PatchMapping("/{id}/status")
    public Payment updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String statusStr = body.get("status");
        String reason    = body.get("reason");
        if (statusStr == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status field is required");
        }
        PaymentStatus status;
        try {
            status = PaymentStatus.valueOf(statusStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status: " + statusStr);
        }
        return paymentService.updatePaymentStatus(id, status, reason);
    }

    @PatchMapping("/{id}/cancel")
    public Payment cancelPayment(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return paymentService.cancelPayment(id, reason);
    }

    @GetMapping("/analytics/dashboard")
    public DashboardAnalyticsResponse getDashboard(@RequestParam(required = false) Long userId) {
        return paymentService.getDashboardAnalytics(userId);
    }
}
