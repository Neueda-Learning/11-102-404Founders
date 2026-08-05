package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.model.DashboardAnalyticsResponse;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.service.AccountService;
import com.Project.PaymentProcessingSystem.service.CrowdfundingCampaignService;
import com.Project.PaymentProcessingSystem.service.PaymentService;
import com.Project.PaymentProcessingSystem.service.SupportTicketService;
import com.Project.PaymentProcessingSystem.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;
	private final AccountService accountService;
	private final PaymentService paymentService;
	private final SupportTicketService supportTicketService;
	private final CrowdfundingCampaignService campaignService;

	public UserController(UserService userService,
	                      AccountService accountService,
	                      PaymentService paymentService,
	                      SupportTicketService supportTicketService,
	                      CrowdfundingCampaignService campaignService) {
		this.userService = userService;
		this.accountService = accountService;
		this.paymentService = paymentService;
		this.supportTicketService = supportTicketService;
		this.campaignService = campaignService;
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

	@GetMapping("/{id}/workspace")
	public Map<String, Object> getUserWorkspace(@PathVariable Long id) {
		User user = userService.getUserById(id);
		List<Account> accounts = accountService.getAccountsByUserId(id);
		List<Payment> payments = paymentService.getAllPayments(id);
		List<SupportTicket> tickets = supportTicketService.getTicketsByUserId(id);
		List<CrowdfundingCampaign> campaigns = campaignService.getAllCampaigns();
		DashboardAnalyticsResponse dashboard = paymentService.getDashboardAnalytics(id);

		Account primaryWallet = accounts.stream()
				.filter(account -> account.getAccountStatus() != null && "ACTIVE".equals(account.getAccountStatus().name()))
				.max((left, right) -> {
					var leftBalance = left.getBalance() == null ? java.math.BigDecimal.ZERO : left.getBalance();
					var rightBalance = right.getBalance() == null ? java.math.BigDecimal.ZERO : right.getBalance();
					return leftBalance.compareTo(rightBalance);
				})
				.orElse(accounts.isEmpty() ? null : accounts.get(0));

		Map<String, Object> response = new HashMap<>();
		response.put("user", user);
		response.put("accounts", accounts);
		response.put("payments", payments);
		response.put("tickets", tickets);
		response.put("campaigns", campaigns);
		response.put("dashboard", dashboard);
		response.put("primaryWallet", primaryWallet);
		return response;
	}

	@PostMapping("/{id}/accounts")
	public ResponseEntity<Account> createUserAccount(@PathVariable Long id, @RequestBody Account account) {
		return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccountForUser(id, account));
	}
}
