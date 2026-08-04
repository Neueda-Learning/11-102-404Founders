package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.Payment;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<@NonNull Payment, @NonNull Long> {
}
