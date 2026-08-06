package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentReversalRequest;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.repository.CrowdfundingCampaignRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentStatusAuditRepository;
import com.Project.PaymentProcessingSystem.repository.SupportTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusAuditRepository auditRepository;

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private CrowdfundingCampaignRepository campaignRepository;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void createPaymentCompletesAndUpdatesBothBalances() {
        Account source = account(1L, "INR", "1000.00", 10L, "9000.00");
        Account destination = account(2L, "INR", "200.00", 20L, "9000.00");

        when(accountService.getAccountById(1L)).thenReturn(source);
        when(accountService.getAccountById(2L)).thenReturn(destination);
        when(accountService.getAccountsByUserId(10L)).thenReturn(List.of(source));

        when(paymentRepository.findTopByIdempotencyKeyOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        when(paymentRepository.sumCompletedOutgoingChargedAmountForUser(org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);

        AtomicLong ids = new AtomicLong(100L);
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(ids.getAndIncrement());
            }
            return p;
        });

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setUserId(10L);
        request.setSourceAccountId(1L);
        request.setDestinationAccountId(2L);
        request.setAmount(new BigDecimal("150.00"));
        request.setCurrencyCode("INR");
        request.setDestinationCurrencyCode("INR");
        request.setPaymentType(PaymentType.NORMAL_PAYMENT);
        request.setIdempotencyKey("unique-payment-1");

        Payment created = paymentService.createPayment(request);

        assertEquals(PaymentStatus.COMPLETED, created.getStatus());
        assertEquals(new BigDecimal("850.00"), source.getBalance().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("350.00"), destination.getBalance().setScale(2, RoundingMode.HALF_UP));
        verify(auditRepository, atLeast(3)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createPaymentInterCurrencyRequiresForexConfirmation() {
        Account source = account(1L, "USD", "1000.00", 10L, "9000.00");
        Account destination = account(2L, "INR", "200.00", 20L, "9000.00");

        when(accountService.getAccountById(1L)).thenReturn(source);
        when(accountService.getAccountById(2L)).thenReturn(destination);

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setUserId(10L);
        request.setSourceAccountId(1L);
        request.setDestinationAccountId(2L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrencyCode("USD");
        request.setDestinationCurrencyCode("INR");
        request.setPaymentType(PaymentType.NORMAL_PAYMENT);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> paymentService.createPayment(request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("forex fee"));
    }

    @Test
    void reverseReceivedPaymentCreatesReversalAndLinksOriginal() {
        Payment original = new Payment();
        original.setId(55L);
        original.setPaymentReference("PAY-ORIG");
        original.setSourceAccountId(1L);
        original.setDestinationAccountId(2L);
        original.setAmount(new BigDecimal("150.00"));
        original.setConvertedAmount(new BigDecimal("150.00"));
        original.setCurrencyCode("INR");
        original.setDestinationCurrencyCode("INR");
        original.setPaymentType(PaymentType.NORMAL_PAYMENT);
        original.setStatus(PaymentStatus.COMPLETED);

        Account sender = account(1L, "INR", "100.00", 10L, "9000.00");
        Account receiver = account(2L, "INR", "500.00", 20L, "9000.00");

        when(paymentRepository.findById(55L)).thenReturn(Optional.of(original));
        when(paymentRepository.existsByOriginalPaymentId(55L)).thenReturn(false);
        when(accountService.getAccountById(2L)).thenReturn(receiver);
        when(accountService.getAccountById(1L)).thenReturn(sender);
        when(accountService.getAccountsByUserId(20L)).thenReturn(List.of(receiver));
        when(paymentRepository.sumCompletedOutgoingChargedAmountForUser(org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(BigDecimal.ZERO);

        AtomicLong ids = new AtomicLong(500L);
        when(paymentRepository.save(org.mockito.ArgumentMatchers.any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(ids.getAndIncrement());
            }
            return p;
        });

        PaymentReversalRequest request = new PaymentReversalRequest();
        request.setUserId(20L);
        request.setReason("Receiver returned amount");

        Payment reversal = paymentService.reverseReceivedPayment(55L, request);

        assertEquals(PaymentStatus.REVERSED, reversal.getStatus());
        assertEquals(new BigDecimal("350.00"), receiver.getBalance().setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("250.00"), sender.getBalance().setScale(2, RoundingMode.HALF_UP));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeast(2)).save(paymentCaptor.capture());
        assertTrue(paymentCaptor.getAllValues().stream().anyMatch(p -> p.getOriginalPaymentId() != null));
    }

    private Account account(Long id, String currency, String balance, Long userId, String dailyLimit) {
        User user = new User();
        user.setId(userId);
        user.setDailyTransactionLimit(new BigDecimal(dailyLimit));

        Account account = new Account();
        account.setId(id);
        account.setCurrencyCode(currency);
        account.setBalance(new BigDecimal(balance));
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setMaxDailyLimit(new BigDecimal("50000.00"));
        account.setUser(user);
        account.setAccountNumber("ACC-" + id);
        return account;
    }
}

