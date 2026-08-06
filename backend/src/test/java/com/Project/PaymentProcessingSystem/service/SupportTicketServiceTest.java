package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.DisputeRole;
import com.Project.PaymentProcessingSystem.model.DisputeTicketRequest;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketPriority;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import com.Project.PaymentProcessingSystem.model.TicketType;
import com.Project.PaymentProcessingSystem.model.TransactionTicketRequest;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository supportTicketRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private SupportTicketService supportTicketService;

    @Test
    void createTicketAppliesDefaultFields() {
        User user = new User();
        user.setId(41L);

        Account account = new Account();
        account.setId(11L);
        account.setUser(user);

        when(accountService.getAccountById(11L)).thenReturn(account);
        when(supportTicketRepository.save(org.mockito.ArgumentMatchers.any(SupportTicket.class))).thenAnswer(invocation -> {
            SupportTicket saved = invocation.getArgument(0);
            saved.setId(900L);
            return saved;
        });

        SupportTicket ticket = new SupportTicket();
        ticket.setAccountId(11L);
        ticket.setTitle("Payment issue");
        ticket.setDescription("Need help");

        SupportTicket result = supportTicketService.createTicket(ticket);

        assertEquals(900L, result.getId());
        assertEquals(41L, result.getUserId());
        assertEquals(TicketPriority.MEDIUM, result.getPriority());
        assertEquals(TicketStatus.OPEN, result.getStatus());
        assertEquals(TicketType.GENERAL, result.getTicketType());
        assertEquals(DisputeRole.NONE, result.getDisputeRole());
    }

    @Test
    void createDisputeTicketRejectsSenderMismatch() {
        DisputeTicketRequest request = new DisputeTicketRequest();
        request.setPaymentId(77L);
        request.setAccountId(20L);
        request.setDisputeRole(DisputeRole.SENDER);
        request.setReason("I did not authorize this");

        Payment payment = new Payment();
        payment.setId(77L);
        payment.setSourceAccountId(10L);
        payment.setDestinationAccountId(20L);

        when(accountService.getAccountById(20L)).thenReturn(new Account());
        when(paymentService.getPaymentById(77L)).thenReturn(payment);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> supportTicketService.createDisputeTicket(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Sender disputes must be raised from the source account", ex.getReason());
    }

    @Test
    void createTransactionTicketCreatesTicketForPaymentOwner() {
        User user = new User();
        user.setId(50L);

        Account source = new Account();
        source.setId(31L);
        source.setUser(user);

        Payment payment = new Payment();
        payment.setId(88L);
        payment.setPaymentReference("PAY-1");
        payment.setSourceAccountId(31L);

        when(paymentService.getPaymentById(88L)).thenReturn(payment);
        when(accountService.getAccountById(31L)).thenReturn(source);
        when(supportTicketRepository.save(org.mockito.ArgumentMatchers.any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionTicketRequest request = new TransactionTicketRequest();
        request.setUserId(50L);
        request.setDescription("Amount was deducted twice");
        request.setIssueType(TicketType.OTHER);
        request.setPriority(TicketPriority.HIGH);

        supportTicketService.createTransactionTicket(88L, request);

        ArgumentCaptor<SupportTicket> captor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(supportTicketRepository).save(captor.capture());
        assertEquals(88L, captor.getValue().getPaymentId());
        assertEquals(50L, captor.getValue().getUserId());
        assertEquals(TicketStatus.OPEN, captor.getValue().getStatus());
    }

    @Test
    void updateTicketStatusResolvedSetsResolutionTime() {
        SupportTicket existing = new SupportTicket();
        existing.setId(3L);

        when(supportTicketRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(supportTicketRepository.save(org.mockito.ArgumentMatchers.any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupportTicket updated = supportTicketService.updateTicketStatus(3L, TicketStatus.RESOLVED, "Issue fixed");

        assertEquals(TicketStatus.RESOLVED, updated.getStatus());
        assertEquals("Issue fixed", updated.getResolutionSummary());
    }
}

