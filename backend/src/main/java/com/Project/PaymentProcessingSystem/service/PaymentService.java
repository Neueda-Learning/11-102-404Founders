package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.repository.PaymentRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentStatusAuditRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("USD", "EUR", "GBP", "INR");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    private final PaymentRepository paymentRepository;
    private final PaymentStatusAuditRepository paymentStatusAuditRepository;
    private final AccountService accountService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentStatusAuditRepository paymentStatusAuditRepository,
                          AccountService accountService) {
        this.paymentRepository = paymentRepository;
        this.paymentStatusAuditRepository = paymentStatusAuditRepository;
        this.accountService = accountService;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    public List<PaymentStatusAudit> getPaymentAuditTrail(Long paymentId) {
        getPaymentById(paymentId);
        return paymentStatusAuditRepository.findByPaymentIdOrderByChangedAtAsc(paymentId);
    }

    public Payment createPayment(CreatePaymentRequest request) {
        validateRequest(request);

        String normalizedCurrency = request.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        Account sourceAccount = accountService.getAccountById(request.getSourceAccountId());
        Account destinationAccount = accountService.getAccountById(request.getDestinationAccountId());

        validateAccounts(sourceAccount, destinationAccount, request.getAmount(), normalizedCurrency);

        LocalDateTime createdAt = LocalDateTime.now();
        Payment payment = new Payment();
        payment.setPaymentReference(generatePaymentReference());
        payment.setSourceAccountId(sourceAccount.getId());
        payment.setDestinationAccountId(destinationAccount.getId());
        payment.setAmount(request.getAmount());
        payment.setCurrencyCode(normalizedCurrency);
        payment.setPaymentType(request.getPaymentType() != null ? request.getPaymentType() : PaymentType.REGULAR);
        payment.setCrowdfundingCampaignId(request.getCrowdfundingCampaignId());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setErrorCode(null);
        payment.setCreatedAt(createdAt);
        payment.setCompletedAt(null);

        Payment savedPayment = paymentRepository.save(payment);

        paymentStatusAuditRepository.save(
                new PaymentStatusAudit(null, savedPayment.getId(), null, PaymentStatus.CREATED, createdAt)
        );

        return savedPayment;
    }

    private void validateRequest(CreatePaymentRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        if (request.getSourceAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account is required");
        }

        if (request.getDestinationAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account is required");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be greater than zero");
        }

        if (request.getAmount().scale() > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount can have at most 2 decimal places");
        }

        if (request.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds the maximum allowed limit");
        }

        if (request.getCurrencyCode() == null || request.getCurrencyCode().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency code is required");
        }

        String normalizedCurrency = request.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (normalizedCurrency.length() != 3 || !SUPPORTED_CURRENCIES.contains(normalizedCurrency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency code");
        }

        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination accounts must be different");
        }

        if (request.getPaymentType() == PaymentType.CROWDFUNDING && request.getCrowdfundingCampaignId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Crowdfunding campaign id is required for crowdfunding payments");
        }
    }

    private void validateAccounts(Account sourceAccount, Account destinationAccount, BigDecimal amount, String currencyCode) {
        if (sourceAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account is not active");
        }

        if (destinationAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account is not active");
        }

        if (!currencyCode.equalsIgnoreCase(sourceAccount.getCurrencyCode())
                || !currencyCode.equalsIgnoreCase(destinationAccount.getCurrencyCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment currency must match both account currencies");
        }

        if (sourceAccount.getBalance() == null || sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient funds in source account");
        }
    }

    private String generatePaymentReference() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniquePart = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "PAY-" + datePart + "-" + uniquePart;
    }
}

