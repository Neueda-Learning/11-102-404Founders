package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.DashboardAnalyticsResponse;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentStatusAudit;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.TicketPriority;
import com.Project.PaymentProcessingSystem.model.TicketStatus;
import com.Project.PaymentProcessingSystem.repository.CrowdfundingCampaignRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentStatusAuditRepository;
import com.Project.PaymentProcessingSystem.repository.SupportTicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Service
public class PaymentService {
    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("INR", "USD", "EUR", "GBP");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");
    private static final BigDecimal FOREX_FEE_RATE = new BigDecimal("0.025");
    private static final Map<String, BigDecimal> INR_RATES = new HashMap<>();
    static {
        INR_RATES.put("INR", BigDecimal.ONE);
        INR_RATES.put("USD", new BigDecimal("0.0120"));
        INR_RATES.put("EUR", new BigDecimal("0.0110"));
        INR_RATES.put("GBP", new BigDecimal("0.0095"));
    }
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
    public List<Payment> getPaymentsByStatuses(Set<PaymentStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) return getAllPayments();
        return paymentRepository.findByStatusIn(statuses);
    }
    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
    }
    public List<PaymentStatusAudit> getPaymentAuditTrail(Long paymentId) {
        getPaymentById(paymentId);
        return auditRepository.findByPaymentIdOrderByChangedAtAsc(paymentId);
    }
    public Payment createPayment(CreatePaymentRequest request) {
        validateRequest(request);
        Account source = accountService.getAccountById(request.getSourceAccountId());
        Account dest   = accountService.getAccountById(request.getDestinationAccountId());
        String srcCcy = normalizeCurrency(request.getCurrencyCode());
        String dstCcy = request.getDestinationCurrencyCode() == null || request.getDestinationCurrencyCode().isBlank()
                ? normalizeCurrency(dest.getCurrencyCode())
                : normalizeCurrency(request.getDestinationCurrencyCode());

        accountService.validateAccountOwnedByUser(source, request.getUserId());
        validateAccounts(source, dest, srcCcy, dstCcy, request.getAmount());

        if (!dstCcy.equalsIgnoreCase(dest.getCurrencyCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination currency must match destination account currency");
        }

        BigDecimal fee        = calculateFee(srcCcy, dstCcy, request.getAmount());
        BigDecimal converted  = convert(request.getAmount(), srcCcy, dstCcy);
        BigDecimal totalDebit = request.getAmount().add(fee);
        source.setBalance(source.getBalance().subtract(totalDebit));
        dest.setBalance(dest.getBalance().add(converted));
        accountService.save(source);
        accountService.save(dest);
        Payment payment = new Payment();
        payment.setPaymentReference(generateRef());
        payment.setSourceAccountId(source.getId());
        payment.setDestinationAccountId(dest.getId());
        payment.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        payment.setCurrencyCode(srcCcy);
        payment.setDestinationCurrencyCode(dstCcy);
        payment.setPaymentType(normalizePaymentType(request.getPaymentType()));
        payment.setCrowdfundingCampaignId(request.getCrowdfundingCampaignId());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setIdempotencyKey(resolveIdempotencyKey(request, dstCcy));
        payment.setForexFee(fee);
        payment.setConvertedAmount(converted);
        payment.setErrorCode(null);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setCompletedAt(null);
        Payment saved = paymentRepository.save(payment);
        writeAudit(saved.getId(), null, PaymentStatus.CREATED, "Payment created");
        saved = step(saved, PaymentStatus.VALIDATED);
        saved = step(saved, PaymentStatus.SENT);
        return paymentRepository.save(saved);
    }
    public Payment updatePaymentStatus(Long paymentId, PaymentStatus newStatus, String reason) {
        if (newStatus == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        Payment payment = getPaymentById(paymentId);
        payment = step(payment, newStatus);
        if (newStatus == PaymentStatus.COMPLETED) {
            payment.setCompletedAt(LocalDateTime.now());
            payment.setErrorCode(null);
            if (payment.getPaymentType() == PaymentType.CROWDFUNDING || payment.getPaymentType() == PaymentType.CROWDFUNDING_PAYMENT) {
                contributeToCampaign(payment);
            }
        }
        if (newStatus == PaymentStatus.FAILED) {
            payment.setCompletedAt(LocalDateTime.now());
            if (reason != null && !reason.isBlank()) payment.setErrorCode(reason.trim());
            createFailureTicket(payment, reason);
        }
        return paymentRepository.save(payment);
    }
    public Payment cancelPayment(Long paymentId, String reason) {
        Payment payment = getPaymentById(paymentId);
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a terminal payment");
        }
        payment = step(payment, PaymentStatus.FAILED);
        payment.setErrorCode(reason != null && !reason.isBlank() ? reason.trim() : "Cancelled by user");
        payment.setCompletedAt(LocalDateTime.now());
        createFailureTicket(payment, payment.getErrorCode());
        return paymentRepository.save(payment);
    }
    public DashboardAnalyticsResponse getDashboardAnalytics() {
        List<Payment> payments = paymentRepository.findAll();
        long total      = payments.size();
        long successful = payments.stream().filter(p -> p.getStatus() == PaymentStatus.COMPLETED).count();
        long failed     = payments.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        long pending    = total - successful - failed;
        BigDecimal totalAmount = payments.stream()
                .map(p -> p.getAmount() == null ? BigDecimal.ZERO : p.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal successPct = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(successful * 100).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        BigDecimal avgAmount = total == 0 ? BigDecimal.ZERO
                : totalAmount.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        DashboardAnalyticsResponse r = new DashboardAnalyticsResponse();
        r.setTotalPayments(total);
        r.setSuccessfulPayments(successful);
        r.setFailedPayments(failed);
        r.setPendingPayments(pending);
        r.setSuccessPercentage(successPct);
        r.setTotalAmountProcessed(totalAmount);
        r.setAverageTransactionAmount(avgAmount);
        return r;
    }
    private Payment step(Payment payment, PaymentStatus next) {
        PaymentStatus current = payment.getStatus();
        if (current == next) return payment;
        validateTransition(current, next);
        payment.setStatus(next);
        writeAudit(payment.getId(), current, next, "Status updated to " + next);
        return payment;
    }
    private void validateTransition(PaymentStatus from, PaymentStatus to) {
        if (from == null) return;
        Map<PaymentStatus, Set<PaymentStatus>> map = Map.of(
                PaymentStatus.CREATED,   EnumSet.of(PaymentStatus.VALIDATED, PaymentStatus.FAILED),
                PaymentStatus.VALIDATED, EnumSet.of(PaymentStatus.SENT,      PaymentStatus.FAILED),
                PaymentStatus.SENT,      EnumSet.of(PaymentStatus.COMPLETED,  PaymentStatus.FAILED),
                PaymentStatus.COMPLETED, EnumSet.noneOf(PaymentStatus.class),
                PaymentStatus.FAILED,    EnumSet.noneOf(PaymentStatus.class)
        );
        if (!map.getOrDefault(from, EnumSet.noneOf(PaymentStatus.class)).contains(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid transition from " + from + " to " + to);
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
        var source = accountService.getAccountById(payment.getSourceAccountId());
        if (source.getUser() != null) {
            ticket.setUserId(source.getUser().getId());
        }
        ticket.setTitle("Payment Failed - " + payment.getPaymentReference());
        ticket.setDescription(reason != null && !reason.isBlank() ? reason : "Automatic failure ticket");
        ticket.setFailureReason(reason);
        ticket.setPriority(TicketPriority.HIGH);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setTicketType(com.Project.PaymentProcessingSystem.model.TicketType.FAILED_PAYMENT);
        ticket.setDisputeRole(com.Project.PaymentProcessingSystem.model.DisputeRole.NONE);
        ticket.setRecoveryRequested(Boolean.FALSE);
        ticket.setCreatedAt(LocalDateTime.now());
        ticketRepository.save(ticket);
    }
    private void contributeToCampaign(Payment payment) {
        if (payment.getCrowdfundingCampaignId() == null) return;
        campaignRepository.findById(payment.getCrowdfundingCampaignId()).ifPresent(campaign -> {
            BigDecimal current = campaign.getCurrentAmount() == null ? BigDecimal.ZERO : campaign.getCurrentAmount();
            String targetCcy = campaign.getTargetCurrency() != null
                    ? normalizeCurrency(campaign.getTargetCurrency()) : payment.getCurrencyCode();
            campaign.setCurrentAmount(current.add(convert(payment.getAmount(), payment.getCurrencyCode(), targetCcy)));
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
        if (req.getSourceAccountId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account required");
        if (req.getDestinationAccountId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account required");
        if (req.getSourceAccountId().equals(req.getDestinationAccountId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source and destination must differ");
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        if (req.getAmount().compareTo(MAX_AMOUNT) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds maximum limit");
        String currency = normalizeCurrency(req.getCurrencyCode());
        if (!SUPPORTED_CURRENCIES.contains(currency))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency: " + currency);
        if (req.getDestinationCurrencyCode() != null && !req.getDestinationCurrencyCode().isBlank()) {
            String destinationCurrency = normalizeCurrency(req.getDestinationCurrencyCode());
            if (!SUPPORTED_CURRENCIES.contains(destinationCurrency)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported destination currency: " + destinationCurrency);
            }
        }
        if ((req.getPaymentType() == PaymentType.CROWDFUNDING || req.getPaymentType() == PaymentType.CROWDFUNDING_PAYMENT)
                && req.getCrowdfundingCampaignId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign ID required for crowdfunding payments");
    }
    private void validateAccounts(Account src, Account dst, String srcCcy, String dstCcy, BigDecimal amount) {
        if (src.getAccountStatus() != AccountStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source account is not active");
        if (dst.getAccountStatus() != AccountStatus.ACTIVE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination account is not active");
        if (!srcCcy.equalsIgnoreCase(src.getCurrencyCode()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment currency must match source account currency");
        if (src.getMaxDailyLimit() != null && amount.compareTo(src.getMaxDailyLimit()) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds account daily limit");
        BigDecimal fee = calculateFee(srcCcy, dstCcy, amount);
        if (src.getBalance() == null || src.getBalance().compareTo(amount.add(fee)) < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance in source account");
    }

    private PaymentType normalizePaymentType(PaymentType paymentType) {
        if (paymentType == null || paymentType == PaymentType.NORMAL_PAYMENT) {
            return PaymentType.REGULAR;
        }
        if (paymentType == PaymentType.CROWDFUNDING_PAYMENT) {
            return PaymentType.CROWDFUNDING;
        }
        return paymentType;
    }

    private String resolveIdempotencyKey(CreatePaymentRequest request, String destinationCurrency) {
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            return request.getIdempotencyKey().trim();
        }
        String raw = request.getUserId() + "|" + request.getSourceAccountId() + "|" + request.getDestinationAccountId() + "|"
                + request.getAmount() + "|" + request.getCurrencyCode() + "|" + destinationCurrency + "|"
                + request.getPaymentType() + "|" + request.getCrowdfundingCampaignId();
        return UUID.nameUUIDFromBytes(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    private BigDecimal calculateFee(String src, String dst, BigDecimal amount) {
        return src.equals(dst) ? BigDecimal.ZERO
                : amount.multiply(FOREX_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
    }
    private BigDecimal convert(BigDecimal amount, String src, String dst) {
        if (src.equals(dst)) return amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal srcRate = INR_RATES.get(src);
        BigDecimal dstRate = INR_RATES.get(dst);
        if (srcRate == null || dstRate == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported currency pair: " + src + " -> " + dst);
        return amount.divide(srcRate, 6, RoundingMode.HALF_UP).multiply(dstRate).setScale(2, RoundingMode.HALF_UP);
    }
    private String normalizeCurrency(String code) {
        if (code == null || code.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Currency code required");
        return code.trim().toUpperCase(Locale.ROOT);
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
