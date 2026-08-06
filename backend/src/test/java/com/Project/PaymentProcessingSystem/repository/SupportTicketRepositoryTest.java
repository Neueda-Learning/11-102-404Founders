package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.DisputeRole;
import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketPriority;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import com.Project.PaymentProcessingSystem.model.TicketType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SupportTicketRepositoryTest {

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Test
    void repositoryFiltersByStatusPaymentUserAndType() {
        saveTicket(10L, 1L, TicketStatus.OPEN, TicketType.GENERAL, "TKT-1");
        saveTicket(20L, 2L, TicketStatus.RESOLVED, TicketType.FAILED_PAYMENT, "TKT-2");

        List<SupportTicket> open = supportTicketRepository.findByStatus(TicketStatus.OPEN);
        List<SupportTicket> byPayment = supportTicketRepository.findByPaymentId(20L);
        List<SupportTicket> byUser = supportTicketRepository.findByUserId(2L);
        List<SupportTicket> byType = supportTicketRepository.findByTicketType(TicketType.FAILED_PAYMENT);

        assertEquals(1, open.size());
        assertEquals("TKT-1", open.get(0).getTicketNumber());
        assertEquals(1, byPayment.size());
        assertEquals("TKT-2", byPayment.get(0).getTicketNumber());
        assertEquals(1, byUser.size());
        assertEquals(1, byType.size());
    }

    private void saveTicket(Long paymentId, Long userId, TicketStatus status, TicketType type, String number) {
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(number);
        ticket.setPaymentId(paymentId);
        ticket.setAccountId(1L);
        ticket.setUserId(userId);
        ticket.setTitle("Issue " + number);
        ticket.setDescription("Description " + number);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticket.setStatus(status);
        ticket.setTicketType(type);
        ticket.setDisputeRole(DisputeRole.NONE);
        ticket.setRecoveryRequested(Boolean.FALSE);
        ticket.setCreatedAt(LocalDateTime.now());
        supportTicketRepository.save(ticket);
    }
}

