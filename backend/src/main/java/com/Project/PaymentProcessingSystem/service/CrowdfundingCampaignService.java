package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.repository.CrowdfundingCampaignRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CrowdfundingCampaignService {

    private final CrowdfundingCampaignRepository campaignRepository;
    private final AccountService accountService;

    public CrowdfundingCampaignService(CrowdfundingCampaignRepository campaignRepository, AccountService accountService) {
        this.campaignRepository = campaignRepository;
        this.accountService = accountService;
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
        if (campaign.getCreatedAt() == null) {
            campaign.setCreatedAt(LocalDateTime.now());
        }

        return campaignRepository.save(campaign);
    }
}

