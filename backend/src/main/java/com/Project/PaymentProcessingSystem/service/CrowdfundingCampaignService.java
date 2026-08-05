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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CrowdfundingCampaignService {

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

    public CrowdfundingCampaign createCampaign(CrowdfundingCampaign campaign) {
        if (campaign == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign payload is required");
        }
        if (campaign.getCampaignName() == null || campaign.getCampaignName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign name is required");
        }
        if (campaign.getBucketAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bucket account id is required");
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

        Account bucketAccount = accountService.getAccountById(campaign.getBucketAccountId());
        if (bucketAccount.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bucket account must be active");
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
        }
        if (campaign.getCreatedAt() == null) {
            campaign.setCreatedAt(LocalDateTime.now());
        }

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
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only ACTIVE campaigns accept contributions");
        }
        if (campaign.getCampaignEndDate() != null && campaign.getCampaignEndDate().isBefore(LocalDate.now())) {
            campaign.setStatus(CampaignStatus.CANCELLED);
            campaignRepository.save(campaign);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign has ended");
        }

        BigDecimal currentAmount = campaign.getCurrentAmount() == null ? BigDecimal.ZERO : campaign.getCurrentAmount();
        BigDecimal targetAmount = campaign.getTargetAmount() == null ? BigDecimal.ZERO : campaign.getTargetAmount();
        if (targetAmount.compareTo(BigDecimal.ZERO) > 0 && currentAmount.compareTo(targetAmount) >= 0) {
            campaign.setStatus(CampaignStatus.COMPLETED);
            campaignRepository.save(campaign);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Campaign target has been reached");
        }

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
        return paymentService.createPayment(paymentRequest);
    }
}

