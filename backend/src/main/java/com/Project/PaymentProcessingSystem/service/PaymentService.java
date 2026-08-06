package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
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
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("INR", "USD");
    private static final BigDecimal FOREX_FEE_RATE = new BigDecimal("0.018");
    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("5000.00");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal EXCHANGE_RATE_USD_TO_INR = new BigDecimal("93.00");

    private final PaymentRepository paymentRepository;
    private final PaymentStatusAuditRepository auditRepository;
    private final SupportTicketRepository ticketRepository;
    private final CrowdfundingCampaignRepository campaignRepository;
    private final AccountService accountService;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentStatusAuditRepository auditRepository,
                          SupportTicketRepository ticketRepository,
                          CrowdfundingCampaignRepository campaignRepository,
                          AccountService accountService) {
        this.paymentRepository = paymentRepository;
        this.auditRepository = auditRepository;
        this.ticketRepository = ticketRepository;
        this.campaignRepository = campaignRepository;
        this.accountService = accountService;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<Payment> getAllPayments(Long userId) {
        if (userId == null) {
            return getAllPayments();
        }
        return paymentRepository.findByUserScope(userId);
    }

    public List<Payment> getPaymentsByStatuses(Set<PaymentStatus> statuses) {
        return getPaymentsByStatuses(statuses, null);
    }

    public List<Payment> getPaymentsByStatuses(Set<PaymentStatus> statuses, Long userId) {
        if (statuses == null || statuses.isEmpty()) {
            return getAllPayments(userId);
        }
        if (userId != null) {
            return paymentRepository.findByUserScopeAndStatusIn(userId, statuses);
        }
        return paymentRepository.findByStatusIn(statuses);
    }

    public List<Payment> findPaymentsForWorkspace(Long userId,
                                                  Set<PaymentStatus> statuses,
                                                  PaymentType paymentType,
                                                  String currency,
                                                  String senderName,
                                                  String receiverName,
                                                  LocalDate fromDate,
                                                  LocalDate toDate,
                                                  LocalDate exactDate,
                                                  BigDecimal minAmount,
                                                  BigDecimal maxAmount,
                                                  Long sourceAccountId,
                                                  Long destinationAccountId,
                                                  String reference,
                                                  String sortBy,
                                                  String sortDir) {
        List<Payment> filtered = new ArrayList<>(getPaymentsByStatuses(statuses, userId));

        if (paymentType != null) {
            filtered.removeIf(payment -> payment.getPaymentType() != paymentType);
        }

        if (reference != null && !reference.trim().isEmpty()) {
            String normalized = reference.trim().toLowerCase(Locale.ROOT);
            filtered.removeIf(payment -> payment.getPaymentReference() == null
                    || !payment.getPaymentReference().toLowerCase(Locale.ROOT).contains(normalized));
        }

        if (currency != null && !currency.trim().isEmpty()) {
            String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
            filtered.removeIf(payment -> payment.getCurrencyCode() == null
                    || !normalizedCurrency.equalsIgnoreCase(payment.getCurrencyCode()));
        }

        if (senderName != null && !senderName.trim().isEmpty()) {
            String normalizedSender = senderName.trim().toLowerCase(Locale.ROOT);
            filtered.removeIf(payment -> !matchesAccountName(payment.getSourceAccountId(), normalizedSender));
        }

        if (receiverName != null && !receiverName.trim().isEmpty()) {
            String normalizedReceiver = receiverName.trim().toLowerCase(Locale.ROOT);
            filtered.removeIf(payment -> !matchesAccountName(payment.getDestinationAccountId(), normalizedReceiver));
        }

        if (sourceAccountId != null) {
            filtered.removeIf(payment -> !sourceAccountId.equals(payment.getSourceAccountId()));
        }

        if (destinationAccountId != null) {
            filtered.removeIf(payment -> !destinationAccountId.equals(payment.getDestinationAccountId()));
        }

        if (fromDate != null) {
            filtered.removeIf(payment -> payment.getCreatedAt() == null || payment.getCreatedAt().toLocalDate().isBefore(fromDate));
        }

        if (toDate != null) {
            filtered.removeIf(payment -> payment.getCreatedAt() == null || payment.getCreatedAt().toLocalDate().isAfter(toDate));
        }

        if (exactDate != null) {
            filtered.removeIf(payment -> payment.getCreatedAt() == null || !payment.getCreatedAt().toLocalDate().isEqual(exactDate));
        }

        if (minAmount != null) {
            filtered.removeIf(payment -> payment.getAmount() == null || payment.getAmount().compareTo(minAmount) < 0);
        }

        if (maxAmount != null) {
            filtered.removeIf(payment -> payment.getAmount() == null || payment.getAmount().compareTo(maxAmount) > 0);
        }

        int direction = "asc".equalsIgnoreCase(sortDir) ? 1 : -1;
        if ("amount".equalsIgnoreCase(sortBy)) {
            filtered.sort((left, right) -> {
                BigDecimal leftAmount = left.getAmount() == null ? BigDecimal.ZERO : left.getAmount();
                BigDecimal rightAmount = right.getAmount() == null ? BigDecimal.ZERO : right.getAmount();
                return leftAmount.compareTo(rightAmount) * direction;
            });
        } else {
            filtered.sort((left, right) -> {
                LocalDateTime leftTime = left.getCreatedAt() == null ? LocalDateTime.MIN : left.getCreatedAt();
                LocalDateTime rightTime = right.getCreatedAt() == null ? LocalDateTime.MIN : right.getCreatedAt();
                return leftTime.compareTo(rightTime) * direction;
            });
        }

        return filtered;
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }

    public List<PaymentStatusAudit> getPaymentAuditTrail(Long paymentId) {
        getPaymentById(paymentId);
        return auditRepository.findByPaymentIdOrderByChangedAtAsc(paymentId);
    }

    @Transactional
    public Payment createPayment(CreatePaymentRequest request) {
        validateRequest(request);

        Account source = accountService.getAccountById(request.getSourceAccountId());
        Account destination = accountService.getAccountById(request.getDestinationAccountId());

        String sourceCurrency = normalizeCurrency(request.getCurrencyCode());
        String destinationCurrency = normalizeCurrency(
                request.getDestinationCurrencyCode() == null || request.getDestinationCurrencyCode().isBlank()
                        ? destination.getCurrencyCode()
                        : request.getDestinationCurrencyCode()
        );

        PaymentType normalizedType = normalizePaymentType(request.getPaymentType());
        boolean usdInvolved = isUsdTransaction(sourceCurrency, destinationCurrency);
        if (usdInvolved && !Boolean.TRUE.equals(request.getForexConfirmed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "International currency transaction detected. A 1.8% forex fee must be confirmed before proceeding.");
        }

        BigDecimal originalAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal exchangeRate = resolveExchangeRate(sourceCurrency, destinationCurrency);
        BigDecimal sourceEquivalent = sourceCurrency.equalsIgnoreCase(destinationCurrency)
                ? originalAmount
                : originalAmount.divide(exchangeRate, 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        BigDecimal forexFee = usdInvolved ? calculateForexFee(sourceEquivalent) : BigDecimal.ZERO;
        BigDecimal finalChargedAmount = sourceEquivalent.add(forexFee).setScale(2, RoundingMode.HALF_UP);

        Payment payment = new Payment();
        payment.setPaymentReference(generateRef());
        payment.setSourceAccountId(source.getId());
        payment.setDestinationAccountId(destination.getId());
        payment.setAmount(originalAmount);
        payment.setCurrencyCode(sourceCurrency);
        payment.setDestinationCurrencyCode(destinationCurrency);
        payment.setPaymentType(normalizedType);
        payment.setCrowdfundingCampaignId(request.getCrowdfundingCampaignId());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey(resolveIdempotencyKey(request, destinationCurrency));
        payment.setForexFee(forexFee);
        payment.setConvertedAmount(sourceEquivalent);
        payment.setExchangeRate(exchangeRate);
        payment.setFinalChargedAmount(finalChargedAmount);
        payment.setErrorCode(null);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setCompletedAt(null);

        Payment saved = paymentRepository.save(payment);
        writeAudit(saved.getId(), null, PaymentStatus.CREATED, "Payment created");

        try {
            validateAccounts(source, destination, request, sourceCurrency, destinationCurrency, finalChargedAmount);
            enforceIdempotency(payment);
            CrowdfundingCampaign campaign = null;
            if (normalizedType == PaymentType.CROWDFUNDING_PAYMENT) {
                campaign = validateCampaign(request.getCrowdfundingCampaignId(), destination, destinationCurrency);
            }

            saved = step(saved, PaymentStatus.VALIDATED, "Validation complete");
            saved = step(saved, PaymentStatus.PROCESSING, "Payment is processing");

            source.setBalance(source.getBalance().subtract(finalChargedAmount));
            BigDecimal destinationAmount = saved.getAmount() == null ? BigDecimal.ZERO : saved.getAmount();
            BigDecimal receiverAccountCredit = convertAmount(destinationAmount, destinationCurrency, destination.getCurrencyCode());
            destination.setBalance(destination.getBalance().add(receiverAccountCredit));
            accountService.save(source);
            accountService.save(destination);

            if (campaign != null) {
                BigDecimal current = campaign.getCurrentAmount() == null ? BigDecimal.ZERO : campaign.getCurrentAmount();
                BigDecimal campaignContribution = convertAmount(destinationAmount, destinationCurrency, campaign.getTargetCurrency());
                BigDecimal updated = current.add(campaignContribution);
                campaign.setCurrentAmount(updated);
                if (campaign.getTargetAmount() != null && updated.compareTo(campaign.getTargetAmount()) >= 0) {
                    campaign.setStatus(CampaignStatus.COMPLETED);
                }
                campaignRepository.save(campaign);
                if (campaign.getStatus() == CampaignStatus.COMPLETED) {
                    settleCampaignBucket(campaign);
                }
            }

            saved = step(saved, PaymentStatus.COMPLETED,
                    buildCompletionDescription(originalAmount, sourceCurrency, destinationCurrency, sourceEquivalent, forexFee, exchangeRate));
            saved.setCompletedAt(LocalDateTime.now());
            saved.setErrorCode(null);
            BigDecimal remaining = calculateRemainingDailyLimit(request.getUserId());
            saved.setRemainingDailyLimit(remaining);
            return paymentRepository.save(saved);
        } catch (ResponseStatusException ex) {
            saved.setErrorCode(ex.getReason());
            saved = step(saved, PaymentStatus.FAILED, ex.getReason() == null ? "Payment failed" : ex.getReason());
            saved.setCompletedAt(LocalDateTime.now());
            createFailureTicket(saved, saved.getErrorCode());
            return paymentRepository.save(saved);
        }
    }

    public Payment updatePaymentStatus(Long paymentId, PaymentStatus newStatus, String reason) {
        if (newStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }
        Payment payment = getPaymentById(paymentId);
        payment = step(payment, newStatus, reason);

        if (newStatus == PaymentStatus.COMPLETED) {
            payment.setCompletedAt(LocalDateTime.now());
            payment.setErrorCode(null);
            if (payment.getPaymentType() == PaymentType.CROWDFUNDING || payment.getPaymentType() == PaymentType.CROWDFUNDING_PAYMENT) {
                contributeToCampaign(payment);
            }
        }

        if (newStatus == PaymentStatus.FAILED) {
            payment.setCompletedAt(LocalDateTime.now());
            if (reason != null && !reason.isBlank()) {
                payment.setErrorCode(reason.trim());
            }
            createFailureTicket(payment, reason);
        }
        return paymentRepository.save(payment);
    }

    public Payment cancelPayment(Long paymentId, String reason) {
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a terminal payment");
        }
        payment = step(payment, PaymentStatus.FAILED, reason == null ? "Cancelled by user" : reason);
        payment.setErrorCode(reason != null && !reason.isBlank() ? reason.trim() : "Cancelled by user");
        payment.setCompletedAt(LocalDateTime.now());
        createFailureTicket(payment, payment.getErrorCode());
        return paymentRepository.save(payment);
    }

    public DashboardAnalyticsResponse getDashboardAnalytics() {
        return getDashboardAnalytics(null);
    }

    public DashboardAnalyticsResponse getDashboardAnalytics(Long userId) {
        List<Payment> payments = getAllPayments(userId);
        List<Account> scopedAccounts = userId == null ? accountService.getAllAccounts() : accountService.getAccountsByUserId(userId);

        long total = payments.size();
        long completed = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED).count();
        long failed = payments.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count();
        long pending = payments.stream().filter(this::isPendingStatus).count();

        BigDecimal totalAmount = payments.stream()
                .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal average = total == 0
                ? BigDecimal.ZERO
                : totalAmount.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        BigDecimal successPercent = total == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(completed).multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        BigDecimal totalBalance = scopedAccounts.stream()
                .map(account -> account.getBalance() == null ? BigDecimal.ZERO : account.getBalance())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal dailyLimit = resolveDailyLimit(scopedAccounts, userId);
        BigDecimal spentToday = userId == null ? BigDecimal.ZERO : getTodaySpentAmount(userId);
        BigDecimal remainingDailyLimit = dailyLimit.subtract(spentToday);
        if (remainingDailyLimit.compareTo(BigDecimal.ZERO) < 0) {
            remainingDailyLimit = BigDecimal.ZERO;
        }

        BigDecimal largestTransaction = payments.stream()
                .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        Set<Long> scopedAccountIds = scopedAccounts.stream().map(Account::getId).collect(java.util.stream.Collectors.toSet());
        BigDecimal income = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED)
                .filter(payment -> scopedAccountIds.contains(payment.getDestinationAccountId()))
                .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expense = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED)
                .filter(payment -> scopedAccountIds.contains(payment.getSourceAccountId()))
                .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal crowdfundingDonations = payments.stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED)
                .filter(payment -> payment.getPaymentType() == PaymentType.CROWDFUNDING || payment.getPaymentType() == PaymentType.CROWDFUNDING_PAYMENT)
                .filter(payment -> scopedAccountIds.contains(payment.getSourceAccountId()))
                .map(payment -> payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        DashboardAnalyticsResponse response = new DashboardAnalyticsResponse();
        response.setTotalPayments(total);
        response.setSuccessfulPayments(completed);
        response.setFailedPayments(failed);
        response.setPendingPayments(pending);
        response.setSuccessPercentage(successPercent);
        response.setTotalAmountProcessed(totalAmount);
        response.setAverageTransactionAmount(average);
        response.setTotalBalance(totalBalance);
        response.setDailyTransactionLimit(dailyLimit);
        response.setSpentToday(spentToday);
        response.setRemainingDailyLimit(remainingDailyLimit);
        response.setIncome(income);
        response.setExpense(expense);
        response.setLargestTransaction(largestTransaction);
        response.setCrowdfundingDonations(crowdfundingDonations);
        return response;
    }

    private BigDecimal convertToDestinationCurrency(BigDecimal amount, String sourceCurrency, String destinationCurrency) {
        if (sourceCurrency.equalsIgnoreCase(destinationCurrency)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
        if ("INR".equalsIgnoreCase(sourceCurrency) && "USD".equalsIgnoreCase(destinationCurrency)) {
            return amount.divide(EXCHANGE_RATE_USD_TO_INR, 6, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        }
        if ("USD".equalsIgnoreCase(sourceCurrency) && "INR".equalsIgnoreCase(destinationCurrency)) {
            return amount.multiply(EXCHANGE_RATE_USD_TO_INR).setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String buildCompletionDescription(BigDecimal originalAmount, String sourceCurrency,
                                               BigDecimal convertedAmount, String destinationCurrency,
                                               BigDecimal forexFee) {
        StringBuilder desc = new StringBuilder("Payment completed successfully");
        if (!sourceCurrency.equalsIgnoreCase(destinationCurrency)) {
            desc.append(String.format(" | Converted %s %s → %s %s (1 USD = 93 INR)",
                    originalAmount.toPlainString(), sourceCurrency,
                    convertedAmount.toPlainString(), destinationCurrency));
        }
        if (forexFee != null && forexFee.compareTo(BigDecimal.ZERO) > 0) {
            desc.append(String.format(" | Forex fee: %s %s", forexFee.toPlainString(), sourceCurrency));
        }
        return desc.toString();
    }

    private Payment step(Payment payment, PaymentStatus next, String reason) {
        PaymentStatus current = payment.getStatus();
        if (current == next) {
            return payment;
        }
        validateTransition(current, next);
        payment.setStatus(next);
        writeAudit(payment.getId(), current, next, reason == null || reason.isBlank() ? "Status updated" : reason);
        return payment;
    }

    private void validateTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null) {
            return;
        }
        Map<PaymentStatus, Set<PaymentStatus>> map = Map.of(
                PaymentStatus.CREATED, EnumSet.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
                PaymentStatus.VALIDATED, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED),
                PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
                PaymentStatus.SENT, EnumSet.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED),
                PaymentStatus.COMPLETED, EnumSet.noneOf(PaymentStatus.class),
                PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class)
        );
        if (!map.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class)).contains(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid transition from " + from + " to " + to);
        }
    }

    private void writeAudit(Long paymentId, PaymentStatus from, PaymentStatus to, String description) {
        PaymentStatusAudit audit = new PaymentStatusAudit();
        audit.setPaymentId(paymentId);
        audit.setFromStatus(from);
        audit.setToStatus(to);
        audit.setStatus(to);
        audit.setDescription(description);
        audit.setChangedAt(LocalDateTime.now());
        auditRepository.save(audit);
    }

    private void createFailureTicket(Payment payment, String reason) {
        SupportTicket ticket = new SupportTicket();
        ticket.setTicketNumber(generateTicketNum());
        ticket.setPaymentId(payment.getId());
        ticket.setAccountId(payment.getSourceAccountId());
        Account source = accountService.getAccountById(payment.getSourceAccountId());
        if (source.getUser() != null) {
            ticket.setUserId(source.getUser().getId());
        }
        ticket.setTitle("Payment Failed - " + payment.getPaymentReference());
        ticket.setDescription(reason != null && !reason.isBlank() ? reason : "Automatic failure ticket");
        ticket.setFailureReason(reason);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setTicketType(TicketType.FAILED_PAYMENT);
        ticket.setDisputeRole(DisputeRole.NONE);
        ticket.setRecoveryRequested(Boolean.FALSE);
        ticket.setCreatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
    }

    private void contributeToCampaign(Payment payment) {
        if (payment.getCrowdfundingCampaignId() == null || payment.getAmount() == null) {
            return;
        }
        campaignRepository.findById(payment.getCrowdfundingCampaignId()).ifPresent(campaign -> {
            BigDecimal current = campaign.getCurrentAmount() == null ? BigDecimal.ZERO : campaign.getCurrentAmount();
            BigDecimal updated = current.add(payment.getAmount());
            campaign.setCurrentAmount(updated);
            if (campaign.getTargetAmount() != null && updated.compareTo(campaign.getTargetAmount()) >= 0) {
                campaign.setStatus(CampaignStatus.COMPLETED);
            }
            campaignRepository.save(campaign);
        });
    }

    private void validateRequest(CreatePaymentRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        if (req.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User id is required");
        }
        if (req.getSourceAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account required");
        }
        if (req.getDestinationAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account required");
        }
        if (req.getSourceAccountId().equals(req.getDestinationAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment Failed - Source and destination accounts cannot be the same.");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment Failed - Amount must be greater than zero.");
        }
        if (req.getAmount().compareTo(MAX_AMOUNT) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds maximum limit");
        }
        if (!SUPPORTED_CURRENCIES.contains(normalizeCurrency(req.getCurrencyCode()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment Failed - Unsupported currency selected.");
        }
        if (req.getDestinationCurrencyCode() != null && !req.getDestinationCurrencyCode().isBlank()
                && !SUPPORTED_CURRENCIES.contains(normalizeCurrency(req.getDestinationCurrencyCode()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported destination currency");
        }
        if ((req.getPaymentType() == PaymentType.CROWDFUNDING || req.getPaymentType() == PaymentType.CROWDFUNDING_PAYMENT)
                && req.getCrowdfundingCampaignId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign ID required for crowdfunding payments");
        }

    }

    private void validateAccounts(Account source,
                                  Account destination,
                                  CreatePaymentRequest request,
                                  String sourceCurrency,
                                  String destinationCurrency,
                                  BigDecimal finalChargedAmount) {
        accountService.validateAccountOwnedByUser(source, request.getUserId());

        if (source.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account is not active");
        }
        if (destination.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account is not active");
        }
        if (!sourceCurrency.equalsIgnoreCase(source.getCurrencyCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment currency must match source account currency");
        }
        // Balance check MUST come first before any limit checks
        if (source.getBalance() == null || source.getBalance().compareTo(finalChargedAmount) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment Failed - Insufficient balance in the selected bank account.");
        }

        if (source.getMaxDailyLimit() != null && finalChargedAmount.compareTo(source.getMaxDailyLimit()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds account daily limit");
        }

        BigDecimal todaySpent = getTodaySpentAmount(request.getUserId());
        BigDecimal dailyLimit = resolveDailyLimit(source, request.getUserId());
        if (todaySpent.add(finalChargedAmount).compareTo(dailyLimit) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment Failed - You have exceeded your daily transaction limit for today.");
        }

        if (request.getSourceAccountNumber() != null && !request.getSourceAccountNumber().isBlank()) {
            if (!request.getSourceAccountNumber().trim().equals(source.getAccountNumber())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account number does not match");
            }
        }
        if (request.getDestinationAccountNumber() != null && !request.getDestinationAccountNumber().isBlank()) {
            if (!request.getDestinationAccountNumber().trim().equals(destination.getAccountNumber())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account number does not match");
            }
        }
    }

    private CrowdfundingCampaign validateCampaign(Long campaignId, Account destinationAccount, String destinationCurrency) {
        if (campaignId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign ID required for crowdfunding payments");
        }
        CrowdfundingCampaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));

        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign is not active");
        }
        if (campaign.getCampaignEndDate() != null && campaign.getCampaignEndDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign has ended");
        }
        if (!destinationAccount.getId().equals(campaign.getBucketAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account must be the campaign bucket account");
        }
        BigDecimal current = campaign.getCurrentAmount() == null ? BigDecimal.ZERO : campaign.getCurrentAmount();
        BigDecimal target = campaign.getTargetAmount() == null ? BigDecimal.ZERO : campaign.getTargetAmount();
        if (target.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(target) >= 0) {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaignRepository.save(campaign);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign target already reached");
        }
        return campaign;
    }

    private void settleCampaignBucket(CrowdfundingCampaign campaign) {
        if (campaign == null || campaign.getBucketAccountId() == null || campaign.getCreatorPayoutAccountId() == null) {
            return;
        }

        Account bucket = accountService.getAccountById(campaign.getBucketAccountId());
        Account payout = accountService.getAccountById(campaign.getCreatorPayoutAccountId());
        BigDecimal bucketBalance = bucket.getBalance() == null ? BigDecimal.ZERO : bucket.getBalance().setScale(2, RoundingMode.HALF_UP);
        if (bucketBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        String sourceCurrency = normalizeCurrency(bucket.getCurrencyCode());
        String destinationCurrency = normalizeCurrency(payout.getCurrencyCode());
        BigDecimal exchangeRate = resolveExchangeRate(sourceCurrency, destinationCurrency);
        BigDecimal destinationAmount = convertAmount(bucketBalance, sourceCurrency, destinationCurrency);

        Payment settlement = new Payment();
        settlement.setPaymentReference(generateRef());
        settlement.setSourceAccountId(bucket.getId());
        settlement.setDestinationAccountId(payout.getId());
        settlement.setAmount(destinationAmount);
        settlement.setCurrencyCode(sourceCurrency);
        settlement.setDestinationCurrencyCode(destinationCurrency);
        settlement.setPaymentType(PaymentType.NORMAL_PAYMENT);
        settlement.setCrowdfundingCampaignId(campaign.getId());
        settlement.setStatus(PaymentStatus.CREATED);
        settlement.setIdempotencyKey("campaign-settlement-" + campaign.getId() + "-" + LocalDate.now());
        settlement.setForexFee(BigDecimal.ZERO);
        settlement.setConvertedAmount(bucketBalance);
        settlement.setExchangeRate(exchangeRate);
        settlement.setFinalChargedAmount(bucketBalance);
        settlement.setCreatedAt(LocalDateTime.now());

        Payment saved = paymentRepository.save(settlement);
        writeAudit(saved.getId(), null, PaymentStatus.CREATED, "Campaign bucket settlement created");
        saved = step(saved, PaymentStatus.VALIDATED, "Campaign settlement validated");
        saved = step(saved, PaymentStatus.PROCESSING, "Campaign settlement processing");

        bucket.setBalance(bucket.getBalance().subtract(bucketBalance));
        payout.setBalance((payout.getBalance() == null ? BigDecimal.ZERO : payout.getBalance()).add(destinationAmount));
        accountService.save(bucket);
        accountService.save(payout);

        saved = step(saved, PaymentStatus.COMPLETED,
                "Campaign target reached. Bucket transferred to creator payout account.");
        saved.setCompletedAt(LocalDateTime.now());
        saved.setErrorCode(null);
        paymentRepository.save(saved);
    }

    private PaymentType normalizePaymentType(PaymentType paymentType) {
        if (paymentType == null || paymentType == PaymentType.REGULAR) {
            return PaymentType.NORMAL_PAYMENT;
        }
        if (paymentType == PaymentType.CROWDFUNDING) {
            return PaymentType.CROWDFUNDING_PAYMENT;
        }
        return paymentType;
    }

    private boolean isPendingStatus(Payment payment) {
        return payment.getStatus() == PaymentStatus.CREATED
                || payment.getStatus() == PaymentStatus.VALIDATED
                || payment.getStatus() == PaymentStatus.PROCESSING
                || payment.getStatus() == PaymentStatus.SENT
                || payment.getStatus() == PaymentStatus.INITIATED;
    }

    private String resolveIdempotencyKey(CreatePaymentRequest request, String destinationCurrency) {
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            return request.getIdempotencyKey().trim();
        }
        String raw = request.getUserId() + "|" + request.getSourceAccountId() + "|" + request.getDestinationAccountId() + "|"
                + request.getAmount() + "|" + request.getCurrencyCode() + "|" + destinationCurrency + "|"
                + request.getPaymentType() + "|" + request.getCrowdfundingCampaignId();
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String normalizeCurrency(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency code required");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment Failed - Unsupported currency selected.");
        }
        return normalized;
    }

    private boolean isUsdTransaction(String sourceCurrency, String destinationCurrency) {
        return "USD".equalsIgnoreCase(sourceCurrency) || "USD".equalsIgnoreCase(destinationCurrency);
    }

    private BigDecimal resolveExchangeRate(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || toCurrency == null || fromCurrency.equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE;
        }
        if ("USD".equalsIgnoreCase(fromCurrency) && "INR".equalsIgnoreCase(toCurrency)) {
            return EXCHANGE_RATE_USD_TO_INR;
        }
        if ("INR".equalsIgnoreCase(fromCurrency) && "USD".equalsIgnoreCase(toCurrency)) {
            return BigDecimal.ONE.divide(EXCHANGE_RATE_USD_TO_INR, 6, RoundingMode.HALF_UP);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment Failed - Unsupported currency pair.");
    }

    private BigDecimal convertAmount(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = resolveExchangeRate(fromCurrency, toCurrency);
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildCompletionDescription(BigDecimal destinationAmount,
                                              String sourceCurrency,
                                              String destinationCurrency,
                                              BigDecimal sourceEquivalent,
                                              BigDecimal forexFee,
                                              BigDecimal exchangeRate) {
        StringBuilder desc = new StringBuilder("Payment completed successfully");
        desc.append(" | Destination amount: ")
                .append(destinationAmount.toPlainString()).append(' ').append(destinationCurrency)
                .append(" | Source equivalent: ")
                .append(sourceEquivalent.toPlainString()).append(' ').append(sourceCurrency)
                .append(" | Rate ")
                .append(sourceCurrency).append("->").append(destinationCurrency).append(": ")
                .append(exchangeRate.toPlainString());
        if (forexFee != null && forexFee.compareTo(BigDecimal.ZERO) > 0) {
            desc.append(" | Forex fee: ").append(forexFee.toPlainString()).append(' ').append(sourceCurrency);
        }
        return desc.toString();
    }

    private BigDecimal calculateForexFee(BigDecimal amount) {
        return amount.multiply(FOREX_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    private void enforceIdempotency(Payment payment) {
        paymentRepository.findTopByIdempotencyKeyOrderByCreatedAtDesc(payment.getIdempotencyKey())
                .filter(existing -> !existing.getId().equals(payment.getId()))
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment Failed - Duplicate payment detected.");
                });
    }

    private BigDecimal getTodaySpentAmount(Long userId) {
        if (userId == null) {
            return BigDecimal.ZERO;
        }
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        BigDecimal spent = paymentRepository.sumCompletedOutgoingChargedAmountForUser(userId, start, end);
        return spent == null ? BigDecimal.ZERO : spent;
    }

    private BigDecimal resolveDailyLimit(Account source, Long userId) {
        if (source.getUser() != null && source.getUser().getDailyTransactionLimit() != null) {
            return source.getUser().getDailyTransactionLimit();
        }
        if (userId != null) {
            List<Account> accounts = accountService.getAccountsByUserId(userId);
            return resolveDailyLimit(accounts, userId);
        }
        return DEFAULT_DAILY_LIMIT;
    }

    private BigDecimal resolveDailyLimit(List<Account> scopedAccounts, Long userId) {
        if (scopedAccounts != null && !scopedAccounts.isEmpty()) {
            for (Account account : scopedAccounts) {
                if (account.getUser() != null && account.getUser().getDailyTransactionLimit() != null) {
                    return account.getUser().getDailyTransactionLimit();
                }
            }
        }
        if (userId != null) {
            try {
                return accountService.getAccountsByUserId(userId).stream()
                        .filter(account -> account.getUser() != null && account.getUser().getDailyTransactionLimit() != null)
                        .map(account -> account.getUser().getDailyTransactionLimit())
                        .findFirst()
                        .orElse(DEFAULT_DAILY_LIMIT);
            } catch (ResponseStatusException ex) {
                return DEFAULT_DAILY_LIMIT;
            }
        }
        return DEFAULT_DAILY_LIMIT;
    }

    private BigDecimal calculateRemainingDailyLimit(Long userId) {
        BigDecimal dailyLimit = resolveDailyLimit(new ArrayList<>(), userId);
        BigDecimal spentAfter = getTodaySpentAmount(userId);
        BigDecimal remaining = dailyLimit.subtract(spentAfter);
        return remaining.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : remaining;
    }

    private boolean matchesAccountName(Long accountId, String normalizedQuery) {
        if (accountId == null || normalizedQuery == null || normalizedQuery.isBlank()) {
            return false;
        }
        try {
            Account account = accountService.getAccountById(accountId);
            return account.getAccountHolderName() != null
                    && account.getAccountHolderName().toLowerCase(Locale.ROOT).contains(normalizedQuery);
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    private String generateRef() {
        return "PAY-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String generateTicketNum() {
        return "TKT-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
    }
}
