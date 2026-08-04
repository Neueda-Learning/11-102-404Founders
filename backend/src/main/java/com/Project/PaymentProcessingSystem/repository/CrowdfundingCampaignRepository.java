package com.Project.PaymentProcessingSystem.repository;

import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrowdfundingCampaignRepository extends JpaRepository<@NonNull CrowdfundingCampaign, @NonNull Long> {
    List<CrowdfundingCampaign> findByStatus(CampaignStatus status);
}

