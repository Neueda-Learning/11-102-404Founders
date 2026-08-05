package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.DashboardAnalyticsResponse;
import com.Project.PaymentProcessingSystem.model.DisputeRole;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketPriority;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import com.Project.PaymentProcessingSystem.model.TicketType;
import com.Project.PaymentProcessingSystem.repository.CrowdfundingCampaignRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentStatusAuditRepository;
import com.Project.PaymentProcessingSystem.repository.SupportTicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("INR", "USD", "EUR", "GBP");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal FOREX_FEE_RATE = new BigDecimal("0.025");
    private static final Map<String, BigDecimal> INR_CONVERSION_RATES = new HashMap<>();
    private static final Map<String, Object> IDEMPOTENCY_LOCKS = new ConcurrentHashMap<>();

    static {
        INR_CONVERSION_RATES.put("INR", BigDecimal.ONE);
        INR_CONVERSION_RATES.put("USD", new BigDecimal("0.0120"));
        INR_CONVERSION_RATES.put("EUR", new BigDecimal("0.0110"));
        INR_CONVERSION_RATES.put("GBP", new BigDecimal("0.0095"));
    }

    private final PaymentRepository paymentRepository;
    private final PaymentStatusAuditRepository paymentStatusAuditRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final CrowdfundingCampaignRepository crowdfundingCampaignRepository;
    private final AccountService accountService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentStatusAuditRepository paymentStatusAuditRepository,
                          SupportTicketRepository supportTicketRepository,
                          CrowdfundingCampaignRepository crowdfundingCampaignRepository,
                          AccountService accountService) {
        this.paymentRepository = paymentRepository;
        this.paymentStatusAuditRepository = paymentStatusAuditRepository;
        this.supportTicketRepository = supportTicketRepository;
        this.crowdfundingCampaignRepository = crowdfundingCampaignRepository;
        this.accountService = accountService;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<Payment> getPaymentsByStatuses(Set<PaymentStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return getAllPayments();
        }
        return paymentRepository.findByStatusIn(statuses);
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

        String sourceCurrency = normalizeCurrency(request.getCurrencyCode());
        String destinationCurrency = request.getDestinationCurrencyCode() == null
                ? sourceCurrency
                : normalizeCurrency(request.getDestinationCurrencyCode());
        String idempotencyKey = resolveIdempotencyKey(request, destinationCurrency);
        Object lock = IDEMPOTENCY_LOCKS.computeIfAbsent(idempotencyKey, key -> new Object());

        synchronized (lock) {
            Payment existingPayment = paymentRepository.findTopByIdempotencyKeyOrderByCreatedAtDesc(idempotencyKey).orElse(null);
            if (existingPayment != null) {
                return existingPayment;
            }

            Account sourceAccount = accountService.getAccountById(request.getSourceAccountId());
            Account destinationAccount = accountService.getAccountById(request.getDestinationAccountId());
            validateAccounts(sourceAccount, destinationAccount, request, sourceCurrency, destinationCurrency);

            BigDecimal forexFee = calculateForexFee(sourceCurrency, destinationCurrency, request.getAmount());
            BigDecimal convertedAmount = calculateConvertedAmount(request.getAmount(), destinationCurrency);
            BigDecimal totalDebit = request.getAmount().add(forexFee);

            sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalDebit));
            destinationAccount.setBalance(destinationAccount.getBalance().add(convertedAmount));
            accountService.save(sourceAccount);
            accountService.save(destinationAccount);

            LocalDateTime createdAt = LocalDateTime.now();
            Payment payment = new Payment();
            payment.setPaymentReference(generatePaymentReference());
            payment.setSourceAccountId(sourceAccount.getId());
            payment.setDestinationAccountId(destinationAccount.getId());
            payment.setAmount(request.getAmount());
            payment.setCurrencyCode(sourceCurrency);
            payment.setDestinationCurrencyCode(destinationCurrency);
            payment.setPaymentType(normalizePaymentType(request.getPaymentType()));
            payment.setCrowdfundingCampaignId(request.getCrowdfundingCampaignId());
            payment.setStatus(PaymentStatus.INITIATED);
            payment.setErrorCode(null);
            payment.setIdempotencyKey(idempotencyKey);
            payment.setForexFee(forexFee);
            payment.setConvertedAmount(convertedAmount);
            payment.setCreatedAt(createdAt);
            payment.setCompletedAt(null);

            Payment savedPayment = paymentRepository.save(payment);
            writeAudit(savedPayment.getId(), null, PaymentStatus.INITIATED, "Payment initiated");

            Payment processingPayment = transitionStatus(savedPayment, PaymentStatus.PROCESSING, "Payment is being processed");
            return paymentRepository.save(processingPayment);
        }
    }

    public Payment updatePaymentStatus(Long paymentId, PaymentStatus newStatus, String reason) {
        if (newStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        Payment payment = getPaymentById(paymentId);
        Payment updated = transitionStatus(payment, newStatus, reason);

        if (newStatus == PaymentStatus.SUCCESS || newStatus == PaymentStatus.COMPLETED) {
            if (updated.getPaymentType() == PaymentType.CROWDFUNDING || updated.getPaymentType() == PaymentType.CROWDFUNDING_PAYMENT) {
                contributeToCampaign(updated);
            }
            updated.setCompletedAt(LocalDateTime.now());
        }

        if (newStatus == PaymentStatus.FAILED) {
            updated.setCompletedAt(LocalDateTime.now());
            if (reason != null && !reason.trim().isEmpty()) {
                updated.setErrorCode(reason.trim());
            }
            createFailureTicket(updated, reason);
        }

        return paymentRepository.save(updated);
    }

    public Payment cancelPayment(Long paymentId, String reason) {
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment can only be cancelled when it is PROCESSING");
        }
        Payment cancelled = transitionStatus(payment, PaymentStatus.CANCELLED,
                reason == null || reason.trim().isEmpty() ? "Cancelled by user" : reason);
        cancelled.setCompletedAt(LocalDateTime.now());
        return paymentRepository.save(cancelled);
    }

    public DashboardAnalyticsResponse getDashboardAnalytics() {
        List<Payment> payments = paymentRepository.findAll();
        long totalPayments = payments.size();
        long successfulPayments = payments.stream().filter(this::isSuccessStatus).count();
        long failedPayments = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count();
        long pendingPayments = payments.stream().filter(this::isPendingStatus).count();

        BigDecimal totalAmount = payments.stream()
                .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal successPercentage = totalPayments == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(successfulPayments)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP);

        BigDecimal averageAmount = totalPayments == 0
                ? BigDecimal.ZERO
                : totalAmount.divide(BigDecimal.valueOf(totalPayments), 2, RoundingMode.HALF_UP);

        DashboardAnalyticsResponse response = new DashboardAnalyticsResponse();
        response.setTotalPayments(totalPayments);
        response.setSuccessfulPayments(successfulPayments);
        response.setFailedPayments(failedPayments);
        response.setPendingPayments(pendingPayments);
        response.setSuccessPercentage(successPercentage);
        response.setTotalAmountProcessed(totalAmount);
        response.setAverageTransactionAmount(averageAmount);
        return response;
    }

    private Payment transitionStatus(Payment payment, PaymentStatus newStatus, String reason) {
        PaymentStatus currentStatus = payment.getStatus();
        if (currentStatus == newStatus) {
            return payment;
        }
        validateTransition(currentStatus, newStatus);
        payment.setStatus(newStatus);
        writeAudit(payment.getId(), currentStatus, newStatus, reason == null ? "Status updated" : reason);
        return payment;
    }

    private void validateTransition(PaymentStatus currentStatus, PaymentStatus newStatus) {
        if (currentStatus == null) {
            return;
        }

        Map<PaymentStatus, Set<PaymentStatus>> transitionMap = Map.of(
                PaymentStatus.INITIATED, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED, PaymentStatus.CANCELLED),
                PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED, PaymentStatus.CANCELLED),
                PaymentStatus.SUCCESS, EnumSet.noneOf(PaymentStatus.class),
                PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class),
                PaymentStatus.CANCELLED, EnumSet.noneOf(PaymentStatus.class),
                PaymentStatus.CREATED, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED, PaymentStatus.CANCELLED),
                PaymentStatus.VALIDATED, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED),
                PaymentStatus.SENT, EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED),
                PaymentStatus.COMPLETED, EnumSet.noneOf(PaymentStatus.class)
        );

        Set<PaymentStatus> allowedStatuses = transitionMap.getOrDefault(currentStatus, EnumSet.noneOf(PaymentStatus.class));
        if (!allowedStatuses.contains(newStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid payment status transition from " + currentStatus + " to " + newStatus);
        }
    }

    private void writeAudit(Long paymentId, PaymentStatus fromStatus, PaymentStatus toStatus, String description) {
        PaymentStatusAudit audit = new PaymentStatusAudit();
        audit.setPaymentId(paymentId);
        audit.setFromStatus(fromStatus);
        audit.setToStatus(toStatus);
        audit.setStatus(toStatus);
        audit.setDescription(description);
        audit.setChangedAt(LocalDateTime.now());
        paymentStatusAuditRepository.save(audit);
    }

    private void createFailureTicket(Payment payment, String reason) {
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(generateFailureTicketNumber());
        ticket.setPaymentId(payment.getId());
        ticket.setAccountId(payment.getSourceAccountId());
        ticket.setTitle("Payment Failed - " + payment.getPaymentReference());
        ticket.setDescription("Automatic failure ticket generated by payment workflow");
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setTicketType(TicketType.FAILED_PAYMENT);
        ticket.setDisputeRole(DisputeRole.NONE);
        ticket.setFailureReason(reason);
        ticket.setRecoveryRequested(Boolean.FALSE);
        ticket.setCreatedAt(LocalDateTime.now());
        supportTicketRepository.save(ticket);
    }

    private void contributeToCampaign(Payment payment) {
        if (payment.getCrowdfundingCampaignId() == null) {
            return;
        }
        crowdfundingCampaignRepository.findById(payment.getCrowdfundingCampaignId()).ifPresent(campaign -> {
            BigDecimal currentAmount = campaign.getCurrentAmount() == null ? BigDecimal.ZERO : campaign.getCurrentAmount();
            BigDecimal increment = payment.getConvertedAmount() == null ? payment.getAmount() : payment.getConvertedAmount();
            campaign.setCurrentAmount(currentAmount.add(increment));
            crowdfundingCampaignRepository.save(campaign);
        });
    }

    private String resolveIdempotencyKey(CreatePaymentRequest request, String destinationCurrency) {
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().trim().isEmpty()) {
            return request.getIdempotencyKey().trim();
        }
        String raw = request.getSourceAccountId() + "|" + request.getDestinationAccountId() + "|" + request.getAmount()
                + "|" + request.getCurrencyCode() + "|" + destinationCurrency + "|" + request.getPaymentType()
                + "|" + request.getCrowdfundingCampaignId();
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String normalizeCurrency(String currencyCode) {
        if (currencyCode == null || currencyCode.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency code is required");
        }
        return currencyCode.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal calculateForexFee(String sourceCurrency, String destinationCurrency, BigDecimal amount) {
        if (!"INR".equals(sourceCurrency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only INR source currency is supported");
        }
        if ("INR".equals(destinationCurrency)) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(FOREX_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateConvertedAmount(BigDecimal amount, String destinationCurrency) {
        BigDecimal rate = INR_CONVERSION_RATES.get(destinationCurrency);
        if (rate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported destination currency");
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private PaymentType normalizePaymentType(PaymentType paymentType) {
        if (paymentType == null) {
            return PaymentType.NORMAL_PAYMENT;
        }
        if (paymentType == PaymentType.REGULAR) {
            return PaymentType.NORMAL_PAYMENT;
        }
        if (paymentType == PaymentType.CROWDFUNDING) {
            return PaymentType.CROWDFUNDING_PAYMENT;
        }
        return paymentType;
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

        String normalizedCurrency = normalizeCurrency(request.getCurrencyCode());
        if (normalizedCurrency.length() != 3 || !SUPPORTED_CURRENCIES.contains(normalizedCurrency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency code");
        }

        if (request.getDestinationCurrencyCode() != null) {
            String normalizedDestination = normalizeCurrency(request.getDestinationCurrencyCode());
            if (!SUPPORTED_CURRENCIES.contains(normalizedDestination)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported destination currency code");
            }
        }

        if (request.getSourceAccountId().equals(request.getDestinationAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination accounts must be different");
        }

        if ((request.getPaymentType() == PaymentType.CROWDFUNDING
                || request.getPaymentType() == PaymentType.CROWDFUNDING_PAYMENT)
                && request.getCrowdfundingCampaignId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Crowdfunding campaign id is required for crowdfunding payments");
        }
    }

    private void validateAccounts(Account sourceAccount,
                                  Account destinationAccount,
                                  CreatePaymentRequest request,
                                  String sourceCurrency,
                                  String destinationCurrency) {
        accountService.validateAccountOwnedByUser(sourceAccount, request.getUserId());

        if (sourceAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account is not active");
        }
        if (destinationAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account is not active");
        }

        validateAccountDetails(sourceAccount, request.getSourceAccountNumber(), "Source");
        validateAccountDetails(destinationAccount, request.getDestinationAccountNumber(), "Destination");

        if (!sourceCurrency.equalsIgnoreCase(sourceAccount.getCurrencyCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account currency must match request currency");
        }
        if (!destinationCurrency.equalsIgnoreCase(destinationAccount.getCurrencyCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Destination account currency must match destination currency");
        }

        BigDecimal forexFee = calculateForexFee(sourceCurrency, destinationCurrency, request.getAmount());
        BigDecimal totalDebit = request.getAmount().add(forexFee);
        if (sourceAccount.getBalance() == null || sourceAccount.getBalance().compareTo(totalDebit) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient funds in source account after applying forex fee");
        }
    }

    private void validateAccountDetails(Account account, String requestedAccountNumber, String accountTypeLabel) {
        if (account.getAccountNumber() == null || account.getAccountNumber().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    accountTypeLabel + " account number is not configured");
        }
        if (account.getBankName() == null || account.getBankName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    accountTypeLabel + " account bank name is not configured");
        }
        if (account.getBankIfsc() == null || account.getBankIfsc().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    accountTypeLabel + " account bank details are not configured");
        }

        if (requestedAccountNumber != null
                && !requestedAccountNumber.trim().isEmpty()
                && !requestedAccountNumber.trim().equals(account.getAccountNumber())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    accountTypeLabel + " account number does not match");
        }
    }

    private boolean isSuccessStatus(Payment payment) {
        return payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.COMPLETED;
    }

    private boolean isPendingStatus(Payment payment) {
        return payment.getStatus() == PaymentStatus.INITIATED
                || payment.getStatus() == PaymentStatus.PROCESSING
                || payment.getStatus() == PaymentStatus.CREATED
                || payment.getStatus() == PaymentStatus.VALIDATED
                || payment.getStatus() == PaymentStatus.SENT;
    }

    private String generatePaymentReference() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniquePart = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        return "PAY-" + datePart + "-" + uniquePart;
    }

    private String generateFailureTicketNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uniquePart = UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        return "TKT-" + datePart + "-" + uniquePart;
    }
}

