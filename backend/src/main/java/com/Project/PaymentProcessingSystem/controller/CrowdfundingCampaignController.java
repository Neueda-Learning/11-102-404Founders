package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.CampaignContributionRequest;
import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CampaignTrackingResponse;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.service.CrowdfundingCampaignService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
public class CrowdfundingCampaignController {

    private final CrowdfundingCampaignService campaignService;

    public CrowdfundingCampaignController(CrowdfundingCampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @GetMapping
    public List<CrowdfundingCampaign> getCampaigns(@RequestParam(required = false) CampaignStatus status) {
        if (status != null) {
            return campaignService.getCampaignsByStatus(status);
        }
        return campaignService.getAllCampaigns();
    }

    @GetMapping("/{id}")
    public CrowdfundingCampaign getCampaignById(@PathVariable Long id) {
        return campaignService.getCampaignById(id);
    }

    @PostMapping
    public ResponseEntity<CrowdfundingCampaign> createCampaign(@RequestBody CrowdfundingCampaign campaign) {
        CrowdfundingCampaign created = campaignService.createCampaign(campaign);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/contribute")
    public ResponseEntity<Payment> contributeToCampaign(@PathVariable Long id,
                                                        @RequestBody CampaignContributionRequest request) {
        Payment created = campaignService.contributeToCampaign(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}/tracking")
    public CampaignTrackingResponse getCampaignTracking(@PathVariable Long id) {
        return campaignService.getCampaignTracking(id);
    }
}

