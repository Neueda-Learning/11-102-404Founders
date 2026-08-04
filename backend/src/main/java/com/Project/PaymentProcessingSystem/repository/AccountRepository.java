package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.Account;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountRepository extends JpaRepository<@NonNull Account, @NonNull Long> {
}
