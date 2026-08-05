package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.service.AccountService;
import com.Project.PaymentProcessingSystem.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;
	private final AccountService accountService;

	public UserController(UserService userService, AccountService accountService) {
		this.userService = userService;
		this.accountService = accountService;
	}

	@GetMapping
	public List<User> getAllUsers() {
		return userService.getAllUsers();
	}

	@GetMapping("/{id}")
	public User getUserById(@PathVariable Long id) {
		return userService.getUserById(id);
	}

	@PostMapping
	public ResponseEntity<User> createUser(@RequestBody User user) {
		return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(user));
	}

	@GetMapping("/{id}/accounts")
	public List<Account> getUserAccounts(@PathVariable Long id) {
		return accountService.getAccountsByUserId(id);
	}

	@PostMapping("/{id}/accounts")
	public ResponseEntity<Account> createUserAccount(@PathVariable Long id, @RequestBody Account account) {
		return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccountForUser(id, account));
	}
}
