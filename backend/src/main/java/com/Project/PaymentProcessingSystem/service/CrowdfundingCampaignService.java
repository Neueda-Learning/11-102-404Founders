package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CampaignContributionRequest;
import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CampaignTrackingResponse;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.repository.CrowdfundingCampaignRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CrowdfundingCampaignService {

    private static final java.util.Set<String> SUPPORTED_CURRENCIES = java.util.Set.of("INR", "USD");

    private final CrowdfundingCampaignRepository campaignRepository;
    private final AccountService accountService;
    private final PaymentService paymentService;

    public CrowdfundingCampaignService(CrowdfundingCampaignRepository campaignRepository,
                                       AccountService accountService,
                                       PaymentService paymentService) {
        this.campaignRepository = campaignRepository;
        this.accountService = accountService;
        this.paymentService = paymentService;
    }

    public List<CrowdfundingCampaign> getAllCampaigns() {
        return campaignRepository.findAll();
    }

    public List<CrowdfundingCampaign> getCampaignsByStatus(CampaignStatus status) {
        return campaignRepository.findByStatus(status);
    }

    public CrowdfundingCampaign getCampaignById(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    @Transactional
    public CrowdfundingCampaign createCampaign(CrowdfundingCampaign campaign) {
        if (campaign == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign payload is required");
        }
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign name is required");
        }
        Long payoutAccountId = campaign.getCreatorPayoutAccountId() != null
                ? campaign.getCreatorPayoutAccountId()
                : campaign.getBucketAccountId();
        if (payoutAccountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Creator payout account id is required");
        }
        if (campaign.getTargetAmount() == null || campaign.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target amount must be greater than zero");
        }
        if (campaign.getCampaignEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign deadline is required");
        }
        if (campaign.getCampaignEndDate().isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign deadline must be today or a future date");
        }

        Account payoutAccount = accountService.getAccountById(payoutAccountId);
        if (payoutAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Creator payout account must be active");
        }

        if (campaign.getCurrentAmount() == null) {
            campaign.setCurrentAmount(BigDecimal.ZERO);
        }
        if (campaign.getThresholdPercentage() == null) {
            campaign.setThresholdPercentage(100);
        }
        if (campaign.getStatus() == null) {
            campaign.setStatus(CampaignStatus.ACTIVE);
        }
        if (campaign.getTargetCurrency() != null) {
            campaign.setTargetCurrency(campaign.getTargetCurrency().trim().toUpperCase());
            if (!SUPPORTED_CURRENCIES.contains(campaign.getTargetCurrency())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only INR and USD campaigns are supported");
            }
        } else {
            campaign.setTargetCurrency(payoutAccount.getCurrencyCode() == null
                    ? "INR"
                    : payoutAccount.getCurrencyCode().trim().toUpperCase(Locale.ROOT));
        }
        if (campaign.getCreatedAt() == null) {
            campaign.setCreatedAt(LocalDateTime.now());
        }
        // Dedicated bucket wallet per campaign.
        Account bucket = new Account();
        bucket.setUser(payoutAccount.getUser());
        bucket.setAccountHolderName((campaign.getCampaignName() == null ? "Campaign" : campaign.getCampaignName()) + " Bucket");
        bucket.setCurrencyCode(campaign.getTargetCurrency());
        bucket.setBalance(BigDecimal.ZERO);
        bucket.setAccountStatus(AccountStatus.ACTIVE);
        bucket.setIsBucketAccount(Boolean.TRUE);
        bucket.setAccountType("Campaign Bucket");
        bucket.setBankName(payoutAccount.getBankName() == null ? "PayFlow Campaign Vault" : payoutAccount.getBankName());
        bucket.setBankIfsc(payoutAccount.getBankIfsc());
        bucket.setAccountNumber("CB" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT));
        Account createdBucket = accountService.createAccount(bucket);

        campaign.setBucketAccountId(createdBucket.getId());
        campaign.setCreatorPayoutAccountId(payoutAccount.getId());
        return campaignRepository.save(campaign);
    }

    public CampaignTrackingResponse getCampaignTracking(Long campaignId) {
        CrowdfundingCampaign campaign = getCampaignById(campaignId);
        BigDecimal collected = campaign.getCurrentAmount() == null ? BigDecimal.ZERO : campaign.getCurrentAmount();
        BigDecimal target = campaign.getTargetAmount() == null ? BigDecimal.ZERO : campaign.getTargetAmount();
        BigDecimal remaining = target.subtract(collected);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }

        long daysUntilDeadline = 0;
        if (campaign.getCampaignEndDate() != null) {
            daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), campaign.getCampaignEndDate());
            if (daysUntilDeadline < 0) {
                daysUntilDeadline = 0;
            }
        }

        CampaignTrackingResponse response = new CampaignTrackingResponse();
        response.setCampaignId(campaign.getId());
        response.setCampaignTitle(campaign.getCampaignName());
        response.setCollectedAmount(collected);
        response.setTargetAmount(target);
        response.setRemainingAmount(remaining);
        response.setPercentageComplete(target.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : collected.multiply(new BigDecimal("100")).divide(target, 2, RoundingMode.HALF_UP));
        response.setDaysUntilDeadline(daysUntilDeadline);
        response.setStatus(campaign.getStatus());
        return response;
    }

    public Payment contributeToCampaign(Long campaignId, CampaignContributionRequest request) {
        CrowdfundingCampaign campaign = getCampaignById(campaignId);

        CreatePaymentRequest paymentRequest = new CreatePaymentRequest();
        paymentRequest.setSourceAccountId(request.getSourceAccountId());
        paymentRequest.setDestinationAccountId(campaign.getBucketAccountId());
        paymentRequest.setAmount(request.getAmount());
        paymentRequest.setCurrencyCode(request.getCurrencyCode());
        paymentRequest.setDestinationCurrencyCode(campaign.getTargetCurrency());
        paymentRequest.setPaymentType(PaymentType.CROWDFUNDING_PAYMENT);
        paymentRequest.setCrowdfundingCampaignId(campaignId);
        paymentRequest.setIdempotencyKey(request.getIdempotencyKey());
        paymentRequest.setUserId(request.getUserId());
        paymentRequest.setForexConfirmed(request.getForexConfirmed());
        return paymentService.createPayment(paymentRequest);
    }
}

