package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.DashboardAnalyticsResponse;
import com.Project.PaymentProcessingSystem.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final PaymentService paymentService;

    public DashboardController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/analytics")
    public DashboardAnalyticsResponse getAnalytics(@RequestParam Long userId) {
        return paymentService.getDashboardAnalytics(userId);
    }
}

