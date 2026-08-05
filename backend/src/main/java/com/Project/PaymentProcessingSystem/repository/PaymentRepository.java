package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<@NonNull Payment, @NonNull Long> {
	Optional<Payment> findTopByIdempotencyKeyOrderByCreatedAtDesc(String idempotencyKey);

	List<Payment> findByStatusIn(Collection<PaymentStatus> statuses);
}
