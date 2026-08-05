package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.DisputeRole;
import com.Project.PaymentProcessingSystem.model.DisputeTicketRequest;
import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketPriority;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import com.Project.PaymentProcessingSystem.model.TicketType;
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

    public List<SupportTicket> getTicketsByUserId(Long userId) {
        return supportTicketRepository.findByUserId(userId);
    }

    public List<SupportTicket> getTicketsByType(TicketType ticketType) {
        return supportTicketRepository.findByTicketType(ticketType);
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

        var account = accountService.getAccountById(ticket.getAccountId());
        if (ticket.getPaymentId() != null) {
            paymentService.getPaymentById(ticket.getPaymentId());
        }
        if (ticket.getUserId() == null && account.getUser() != null) {
            ticket.setUserId(account.getUser().getId());
        }

        if (ticket.getPriority() == null) {
            ticket.setPriority(TicketPriority.MEDIUM);
        }
        if (ticket.getStatus() == null) {
            ticket.setStatus(TicketStatus.OPEN);
        }
        if (ticket.getTicketType() == null) {
            ticket.setTicketType(TicketType.GENERAL);
        }
        if (ticket.getDisputeRole() == null) {
            ticket.setDisputeRole(DisputeRole.NONE);
        }
        if (ticket.getRecoveryRequested() == null) {
            ticket.setRecoveryRequested(Boolean.FALSE);
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
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ticket status is required");
        }
        SupportTicket ticket = getTicketById(id);
        ticket.setStatus(status);

        if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            ticket.setResolvedAt(LocalDateTime.now());
            ticket.setResolutionSummary(resolutionSummary);
        }

        return supportTicketRepository.save(ticket);
    }

    public SupportTicket createDisputeTicket(DisputeTicketRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dispute payload is required");
        }
        if (request.getPaymentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment id is required");
        }
        if (request.getAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Account id is required");
        }
        if (request.getDisputeRole() == null || request.getDisputeRole() == DisputeRole.NONE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dispute role must be SENDER or RECEIVER");
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dispute reason is required");
        }

        accountService.getAccountById(request.getAccountId());
        var payment = paymentService.getPaymentById(request.getPaymentId());

        if (request.getDisputeRole() == DisputeRole.SENDER
                && !request.getAccountId().equals(payment.getSourceAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Sender disputes must be raised from the source account");
        }

        if (request.getDisputeRole() == DisputeRole.RECEIVER
                && !request.getAccountId().equals(payment.getDestinationAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Receiver disputes must be raised from the destination account");
        }

        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setPaymentId(request.getPaymentId());
        ticket.setAccountId(request.getAccountId());
        ticket.setUserId(request.getUserId());
        ticket.setTitle("Dispute for payment " + request.getPaymentId());
        ticket.setDescription(request.getReason());
        ticket.setFailureReason(request.getReason());
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setTicketType(request.getDisputeRole() == DisputeRole.SENDER
                ? TicketType.DISPUTE_SENDER
                : TicketType.DISPUTE_RECEIVER);
        ticket.setDisputeRole(request.getDisputeRole());
        ticket.setRecoveryRequested(request.getRecoveryRequested() != null ? request.getRecoveryRequested() : Boolean.TRUE);
        ticket.setCreatedAt(LocalDateTime.now());
        return supportTicketRepository.save(ticket);
    }

    private String generateTicketNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return "TKT-" + datePart + "-" + uniquePart;
    }
}

