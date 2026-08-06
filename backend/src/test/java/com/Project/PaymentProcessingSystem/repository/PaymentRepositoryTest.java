package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUserScopeReturnsPaymentsLinkedToUserAccounts() {
        User sender = user("sender@example.com");
        User receiver = user("receiver@example.com");
        User outsider = user("outsider@example.com");

        Account senderAccount = account(sender, "INR", "1000.00");
        Account receiverAccount = account(receiver, "INR", "300.00");
        Account outsiderAccount = account(outsider, "INR", "600.00");

        payment(senderAccount.getId(), receiverAccount.getId(), "100.00", PaymentStatus.COMPLETED, "idem-1", "ref-1");
        payment(outsiderAccount.getId(), receiverAccount.getId(), "50.00", PaymentStatus.CREATED, "idem-2", "ref-2");

        List<Payment> scoped = paymentRepository.findByUserScope(sender.getId());

        assertEquals(1, scoped.size());
        assertEquals("ref-1", scoped.get(0).getPaymentReference());
    }

    @Test
    void findByUserScopeAndStatusInFiltersByStatus() {
        User user = user("u1@example.com");
        User peer = user("u2@example.com");

        Account userAccount = account(user, "INR", "1000.00");
        Account peerAccount = account(peer, "INR", "500.00");

        payment(userAccount.getId(), peerAccount.getId(), "120.00", PaymentStatus.COMPLETED, "idem-a", "ref-a");
        payment(userAccount.getId(), peerAccount.getId(), "90.00", PaymentStatus.FAILED, "idem-b", "ref-b");

        List<Payment> onlyCompleted = paymentRepository.findByUserScopeAndStatusIn(user.getId(), Set.of(PaymentStatus.COMPLETED));

        assertEquals(1, onlyCompleted.size());
        assertEquals(PaymentStatus.COMPLETED, onlyCompleted.get(0).getStatus());
    }

    @Test
    void sumCompletedOutgoingChargedAmountForUserUsesFinalChargedAmount() {
        User user = user("limit@example.com");
        User peer = user("peer@example.com");

        Account userAccount = account(user, "INR", "1000.00");
        Account peerAccount = account(peer, "INR", "500.00");

        Payment p1 = payment(userAccount.getId(), peerAccount.getId(), "100.00", PaymentStatus.COMPLETED, "idem-c", "ref-c");
        p1.setFinalChargedAmount(new BigDecimal("102.00"));
        p1.setCreatedAt(LocalDateTime.now().minusHours(1));
        paymentRepository.save(p1);

        Payment p2 = payment(userAccount.getId(), peerAccount.getId(), "50.00", PaymentStatus.COMPLETED, "idem-d", "ref-d");
        p2.setFinalChargedAmount(new BigDecimal("51.00"));
        p2.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        paymentRepository.save(p2);

        BigDecimal sum = paymentRepository.sumCompletedOutgoingChargedAmountForUser(
                user.getId(),
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(1)
        );

        assertEquals(new BigDecimal("153.00"), sum);
    }

    private User user(String email) {
        User user = new User();
        user.setFullName(email);
        user.setEmail(email);
        user.setDefaultCurrency("INR");
        user.setDailyTransactionLimit(new BigDecimal("5000.00"));
        return userRepository.save(user);
    }

    private Account account(User user, String currency, String balance) {
        Account account = new Account();
        account.setUser(user);
        account.setAccountHolderName(user.getFullName());
        account.setCurrencyCode(currency);
        account.setBalance(new BigDecimal(balance));
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setMaxDailyLimit(new BigDecimal("50000.00"));
        account.setIsBucketAccount(false);
        account.setAccountNumber("ACC-" + user.getId() + "-" + currency);
        return accountRepository.save(account);
    }

    private Payment payment(Long sourceId, Long destinationId, String amount, PaymentStatus status, String idempotency, String reference) {
        Payment payment = new Payment();
        payment.setPaymentReference(reference);
        payment.setSourceAccountId(sourceId);
        payment.setDestinationAccountId(destinationId);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrencyCode("INR");
        payment.setDestinationCurrencyCode("INR");
        payment.setPaymentType(PaymentType.NORMAL_PAYMENT);
        payment.setStatus(status);
        payment.setIdempotencyKey(idempotency);
        payment.setCreatedAt(LocalDateTime.now());
        return paymentRepository.save(payment);
    }
}

