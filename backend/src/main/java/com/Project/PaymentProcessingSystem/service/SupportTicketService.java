package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketPriority;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import com.Project.PaymentProcessingSystem.repository.SupportTicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;
    private final AccountService accountService;
    private final PaymentService paymentService;

    public SupportTicketService(SupportTicketRepository supportTicketRepository,
                                AccountService accountService,
                                PaymentService paymentService) {
        this.supportTicketRepository = supportTicketRepository;
        this.accountService = accountService;
        this.paymentService = paymentService;
    }

    public List<SupportTicket> getAllTickets() {
        return supportTicketRepository.findAll();
    }

    public List<SupportTicket> getTicketsByStatus(TicketStatus status) {
        return supportTicketRepository.findByStatus(status);
    }

    public List<SupportTicket> getTicketsByPaymentId(Long paymentId) {
        return supportTicketRepository.findByPaymentId(paymentId);
    }

    public SupportTicket getTicketById(Long id) {
        return supportTicketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    public SupportTicket createTicket(SupportTicket ticket) {
        if (ticket == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket payload is required");
        }
        if (ticket.getAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account id is required");
        }
        if (ticket.getTitle() == null || ticket.getTitle().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        if (ticket.getDescription() == null || ticket.getDescription().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Description is required");
        }

        accountService.getAccountById(ticket.getAccountId());
        if (ticket.getPaymentId() != null) {
            paymentService.getPaymentById(ticket.getPaymentId());
        }

        if (ticket.getPriority() == null) {
            ticket.setPriority(TicketPriority.MEDIUM);
        }
        if (ticket.getStatus() == null) {
            ticket.setStatus(TicketStatus.OPEN);
        }
        if (ticket.getTicketNumber() == null || ticket.getTicketNumber().trim().isEmpty()) {
            ticket.setTicketNumber(generateTicketNumber());
        }
        if (ticket.getCreatedAt() == null) {
            ticket.setCreatedAt(LocalDateTime.now());
        }

        return supportTicketRepository.save(ticket);
    }

    public SupportTicket updateTicketStatus(Long id, TicketStatus status, String resolutionSummary) {
        SupportTicket ticket = getTicketById(id);
        ticket.setStatus(status);

        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            ticket.setResolvedAt(LocalDateTime.now());
            ticket.setResolutionSummary(resolutionSummary);
        }

        return supportTicketRepository.save(ticket);
    }

    private String generateTicketNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return "TKT-" + datePart + "-" + uniquePart;
    }
}

