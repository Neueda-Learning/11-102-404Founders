package com.Project.PaymentProcessingSystem;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.repository.AccountRepository;
import com.Project.PaymentProcessingSystem.repository.CrowdfundingCampaignRepository;
import com.Project.PaymentProcessingSystem.repository.PaymentRepository;
import com.Project.PaymentProcessingSystem.repository.UserRepository;
import com.Project.PaymentProcessingSystem.service.PaymentService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PaymentRepository paymentRepository;
    private final CrowdfundingCampaignRepository campaignRepository;
    private final PaymentService paymentService;

    public DataInitializer(UserRepository userRepository,
                           AccountRepository accountRepository,
                           PaymentRepository paymentRepository,
                           CrowdfundingCampaignRepository campaignRepository,
                           PaymentService paymentService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.campaignRepository = campaignRepository;
        this.paymentService = paymentService;
    }

    @Override
    public void run(String... args) {
        List<User> demoUsers = ensureDemoUsers();
        ensureCampaigns();

        for (User user : demoUsers) {
            ensureAccountsForUser(user);
            ensurePaymentVolumeForUser(user);
        }
    }

    private List<User> ensureDemoUsers() {
        Map<String, String> demoUsers = Map.of(
                "John Carter", "john.carter@demo.payflow",
                "Emily Watson", "emily.watson@demo.payflow",
                "David Lee", "david.lee@demo.payflow",
                "Sophia Brown", "sophia.brown@demo.payflow",
                "Alex Wilson", "alex.wilson@demo.payflow"
        );

        List<User> users = new ArrayList<>();

        for (Map.Entry<String, String> entry : demoUsers.entrySet()) {
            User user = userRepository.findByEmail(entry.getValue()).orElseGet(() -> {
                User newUser = new User();
                newUser.setFullName(entry.getKey());
                newUser.setEmail(entry.getValue());
                newUser.setPhoneNumber("+1-202-555-" + String.format(Locale.ROOT, "%04d", Math.abs(entry.getKey().hashCode()) % 10000));
                newUser.setAddress("Fintech Avenue, Downtown");
                newUser.setCountry("USA");
                newUser.setDefaultCurrency("USD");
                newUser.setDailyTransactionLimit(new BigDecimal("5000.00"));
                return userRepository.save(newUser);
            });
            users.add(user);
        }

        return users;
    }

    private void ensureAccountsForUser(User user) {
        List<Account> existing = accountRepository.findByUser_Id(user.getId());
        if (existing.size() >= 5) {
            return;
        }

        createAccountIfMissing(user, "Savings Account", "Savings Account", "USD", new BigDecimal("18500.00"), false, "001");
        createAccountIfMissing(user, "Checking Account", "Checking Account", "INR", new BigDecimal("315000.00"), false, "002");
        createAccountIfMissing(user, "Salary Account", "Salary Account", "INR", new BigDecimal("510000.00"), false, "003");
        createAccountIfMissing(user, "Business Account", "Business Account", "USD", new BigDecimal("42000.00"), false, "004");
        createAccountIfMissing(user, "USD Wallet", "USD Wallet", "USD", new BigDecimal("12200.00"), false, "005");
    }

    private void createAccountIfMissing(User user,
                                        String label,
                                        String accountType,
                                        String currency,
                                        BigDecimal balance,
                                        boolean isBucket,
                                        String suffix) {
        String accountNumber = "PFS" + user.getId() + suffix;
        boolean exists = accountRepository.findByUser_Id(user.getId()).stream()
                .anyMatch(account -> accountNumber.equals(account.getAccountNumber()));
        if (exists) {
            return;
        }

        Account account = new Account();
        account.setUser(user);
        account.setAccountHolderName(user.getFullName() + " - " + label);
        account.setAccountNumber(accountNumber);
        account.setBankName("PayFlow Bank");
        account.setBankIfsc("PFLW000" + user.getId());
        account.setCurrencyCode(currency);
        account.setAccountType(accountType);
        account.setBalance(balance);
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setIsBucketAccount(isBucket);
        account.setMaxDailyLimit(new BigDecimal("250000.00"));
        accountRepository.save(account);
    }

    private void ensureCampaigns() {
        List<Account> usdAccounts = accountRepository.findAll().stream()
                .filter(account -> "USD".equalsIgnoreCase(account.getCurrencyCode()))
                .toList();
        if (usdAccounts.isEmpty()) {
            return;
        }

        Account bucket = usdAccounts.get(0);
        createCampaignIfMissing("Flood Relief Fund", "Helping families affected by floods.", bucket, new BigDecimal("95000.00"));
        createCampaignIfMissing("Healthcare for Children", "Providing surgeries and treatments.", bucket, new BigDecimal("80000.00"));
        createCampaignIfMissing("Education Scholarship", "Supporting underprivileged students.", bucket, new BigDecimal("60000.00"));
        createCampaignIfMissing("Animal Rescue Foundation", "Food and shelter for rescued animals.", bucket, new BigDecimal("45000.00"));
        createCampaignIfMissing("Tree Plantation Initiative", "Environmental restoration campaign.", bucket, new BigDecimal("70000.00"));
    }

    private void createCampaignIfMissing(String name, String description, Account bucket, BigDecimal target) {
        boolean exists = campaignRepository.findAll().stream().anyMatch(c -> name.equalsIgnoreCase(c.getCampaignName()));
        if (exists) {
            return;
        }

        CrowdfundingCampaign campaign = new CrowdfundingCampaign();
        campaign.setCampaignName(name);
        campaign.setDescription(description);
        campaign.setDonationCategory("Community");
        campaign.setDonationOptions("100,500,1000,5000");
        campaign.setBucketAccountId(bucket.getId());
        campaign.setTargetAmount(target);
        campaign.setTargetCurrency("USD");
        campaign.setCurrentAmount(new BigDecimal("0.00"));
        campaign.setThresholdPercentage(100);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCampaignEndDate(LocalDate.now().plusMonths(3));
        campaign.setCreatedAt(LocalDateTime.now());
        campaignRepository.save(campaign);
    }

    private void ensurePaymentVolumeForUser(User user) {
        List<Payment> scoped = paymentRepository.findByUserScope(user.getId());
        long completed = scoped.stream().filter(payment -> payment.getStatus() == PaymentStatus.COMPLETED).count();
        long failed = scoped.stream().filter(payment -> payment.getStatus() == PaymentStatus.FAILED).count();
        long pending = scoped.stream().filter(payment -> payment.getStatus() == PaymentStatus.CREATED || payment.getStatus() == PaymentStatus.VALIDATED || payment.getStatus() == PaymentStatus.PROCESSING).count();

        while (completed < 10) {
            if (!createCompletedPayment(user)) {
                break;
            }
            completed++;
        }

        while (failed < 5) {
            if (!createFailedPayment(user)) {
                break;
            }
            failed++;
        }

        while (pending < 3) {
            if (!createPendingPayment(user)) {
                break;
            }
            pending++;
        }
    }

    private boolean createCompletedPayment(User user) {
        List<Account> userAccounts = accountRepository.findByUser_Id(user.getId());
        Account source = userAccounts.stream().filter(account -> "USD".equalsIgnoreCase(account.getCurrencyCode())).findFirst().orElse(null);
        if (source == null) {
            return false;
        }

        Account destination = accountRepository.findAll().stream()
                .filter(account -> !account.getId().equals(source.getId()))
                .filter(account -> source.getCurrencyCode().equalsIgnoreCase(account.getCurrencyCode()))
                .findFirst().orElse(null);
        if (destination == null) {
            return false;
        }

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setUserId(user.getId());
        request.setSourceAccountId(source.getId());
        request.setDestinationAccountId(destination.getId());
        request.setAmount(new BigDecimal("125.00"));
        request.setCurrencyCode(source.getCurrencyCode());
        request.setDestinationCurrencyCode(destination.getCurrencyCode());
        request.setPaymentType(PaymentType.NORMAL_PAYMENT);
        request.setSourceAccountNumber(source.getAccountNumber());
        request.setDestinationAccountNumber(destination.getAccountNumber());
        request.setIdempotencyKey("seed-completed-" + UUID.randomUUID());

        paymentService.createPayment(request);
        return true;
    }

    private boolean createFailedPayment(User user) {
        List<Account> userAccounts = accountRepository.findByUser_Id(user.getId());
        Account source = userAccounts.stream().filter(account -> "USD".equalsIgnoreCase(account.getCurrencyCode())).findFirst().orElse(null);
        if (source == null) {
            return false;
        }

        Account destination = accountRepository.findAll().stream()
                .filter(account -> !account.getId().equals(source.getId()))
                .filter(account -> source.getCurrencyCode().equalsIgnoreCase(account.getCurrencyCode()))
                .findFirst().orElse(null);
        if (destination == null) {
            return false;
        }

        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setUserId(user.getId());
        request.setSourceAccountId(source.getId());
        request.setDestinationAccountId(destination.getId());
        request.setAmount(new BigDecimal("260000.00"));
        request.setCurrencyCode(source.getCurrencyCode());
        request.setDestinationCurrencyCode(destination.getCurrencyCode());
        request.setPaymentType(PaymentType.NORMAL_PAYMENT);
        request.setSourceAccountNumber(source.getAccountNumber());
        request.setDestinationAccountNumber(destination.getAccountNumber());
        request.setIdempotencyKey("seed-failed-" + UUID.randomUUID());

        Payment created = paymentService.createPayment(request);
        return created.getStatus() == PaymentStatus.FAILED;
    }

    private boolean createPendingPayment(User user) {
        List<Account> userAccounts = accountRepository.findByUser_Id(user.getId());
        Account source = userAccounts.stream().filter(account -> "USD".equalsIgnoreCase(account.getCurrencyCode())).findFirst().orElse(null);
        Account destination = userAccounts.stream()
                .filter(account -> !account.getId().equals(source != null ? source.getId() : -1L))
                .filter(account -> source != null && source.getCurrencyCode().equalsIgnoreCase(account.getCurrencyCode()))
                .findFirst().orElse(null);
        if (source == null || destination == null) {
            return false;
        }

        Payment payment = new Payment();
        payment.setPaymentReference("PEND-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        payment.setIdempotencyKey("seed-pending-" + UUID.randomUUID());
        payment.setSourceAccountId(source.getId());
        payment.setDestinationAccountId(destination.getId());
        payment.setAmount(new BigDecimal("75.00"));
        payment.setConvertedAmount(new BigDecimal("75.00"));
        payment.setForexFee(BigDecimal.ZERO);
        payment.setCurrencyCode(source.getCurrencyCode());
        payment.setDestinationCurrencyCode(destination.getCurrencyCode());
        payment.setPaymentType(PaymentType.NORMAL_PAYMENT);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setCreatedAt(LocalDateTime.now().minusHours(1));
        payment.setCompletedAt(null);
        paymentRepository.save(payment);
        return true;
    }
}
