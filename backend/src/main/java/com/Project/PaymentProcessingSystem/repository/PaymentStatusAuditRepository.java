package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentStatusAuditRepository extends JpaRepository<@NonNull PaymentStatusAudit, @NonNull Long> {
    List<PaymentStatusAudit> findByPaymentIdOrderByChangedAtAsc(Long paymentId);
}
