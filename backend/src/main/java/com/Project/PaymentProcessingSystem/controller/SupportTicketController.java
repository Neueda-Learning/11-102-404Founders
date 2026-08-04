package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import com.Project.PaymentProcessingSystem.service.SupportTicketService;
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

@RestController
@RequestMapping("/api/tickets")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    public SupportTicketController(SupportTicketService supportTicketService) {
        this.supportTicketService = supportTicketService;
    }

    @GetMapping
    public List<SupportTicket> getTickets(@RequestParam(required = false) TicketStatus status,
                                          @RequestParam(required = false) Long paymentId) {
        if (status != null) {
            return supportTicketService.getTicketsByStatus(status);
        }
        if (paymentId != null) {
            return supportTicketService.getTicketsByPaymentId(paymentId);
        }
        return supportTicketService.getAllTickets();
    }

    @GetMapping("/{id}")
    public SupportTicket getTicketById(@PathVariable Long id) {
        return supportTicketService.getTicketById(id);
    }

    @PostMapping
    public ResponseEntity<SupportTicket> createTicket(@RequestBody SupportTicket ticket) {
        SupportTicket created = supportTicketService.createTicket(ticket);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/status")
    public SupportTicket updateStatus(@PathVariable Long id,
                                      @RequestParam TicketStatus status,
                                      @RequestParam(required = false) String resolutionSummary) {
        return supportTicketService.updateTicketStatus(id, status, resolutionSummary);
    }
}

