package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.Account;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class AccountRepository {

	private final ConcurrentMap<Long, Account> accounts = new ConcurrentHashMap<>();
	private final AtomicLong idCounter = new AtomicLong(1);

	public List<Account> findAll() {
		return new ArrayList<>(accounts.values());
	}

	public Optional<Account> findById(Long id) {
		return Optional.ofNullable(accounts.get(id));
	}

	public Account save(Account account) {
		if (account.getId() == null) {
			account.setId(idCounter.getAndIncrement());
		}
		accounts.put(account.getId(), account);
		return account;
	}

	public void delete(Account account) {
		if (account != null && account.getId() != null) {
			accounts.remove(account.getId());
		}
	}
}
