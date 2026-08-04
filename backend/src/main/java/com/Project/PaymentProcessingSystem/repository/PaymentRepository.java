package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
