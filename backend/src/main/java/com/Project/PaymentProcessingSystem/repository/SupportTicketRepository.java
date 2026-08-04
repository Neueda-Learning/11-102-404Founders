package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<@NonNull SupportTicket, @NonNull Long> {
    List<SupportTicket> findByStatus(TicketStatus status);

    List<SupportTicket> findByPaymentId(Long paymentId);
}

