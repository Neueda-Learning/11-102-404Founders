package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<@NonNull Payment, @NonNull Long> {
	Optional<Payment> findTopByIdempotencyKeyOrderByCreatedAtDesc(String idempotencyKey);

	List<Payment> findByStatusIn(Collection<PaymentStatus> statuses);

	@Query("""
			select p from Payment p
			where p.sourceAccountId in (select a.id from Account a where a.user.id = :userId)
			   or p.destinationAccountId in (select a.id from Account a where a.user.id = :userId)
			""")
	List<Payment> findByUserScope(@Param("userId") Long userId);

	@Query("""
			select p from Payment p
			where (p.sourceAccountId in (select a.id from Account a where a.user.id = :userId)
			   or p.destinationAccountId in (select a.id from Account a where a.user.id = :userId))
			  and p.status in :statuses
			""")
	List<Payment> findByUserScopeAndStatusIn(@Param("userId") Long userId,
	                                         @Param("statuses") Collection<PaymentStatus> statuses);
}
