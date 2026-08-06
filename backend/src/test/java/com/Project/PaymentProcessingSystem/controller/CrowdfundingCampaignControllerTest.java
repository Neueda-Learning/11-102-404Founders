package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.CampaignContributionRequest;
import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.service.CrowdfundingCampaignService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CrowdfundingCampaignControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CrowdfundingCampaignService campaignService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CrowdfundingCampaignController(campaignService)).build();
    }

    @Test
    void getCampaignsWithStatusUsesFilteredService() throws Exception {
        CrowdfundingCampaign campaign = new CrowdfundingCampaign();
        campaign.setId(1L);
        campaign.setCampaignName("Health Fund");
        campaign.setStatus(CampaignStatus.ACTIVE);

        when(campaignService.getCampaignsByStatus(CampaignStatus.ACTIVE)).thenReturn(List.of(campaign));

        mockMvc.perform(get("/api/campaigns").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].campaignName").value("Health Fund"));
    }

    @Test
    void createCampaignReturnsCreated() throws Exception {
        CrowdfundingCampaign created = new CrowdfundingCampaign();
        created.setId(7L);
        created.setCampaignName("Education");

        when(campaignService.createCampaign(any(CrowdfundingCampaign.class))).thenReturn(created);

        mockMvc.perform(post("/api/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "campaignName", "Education",
                                "bucketAccountId", 10,
                                "targetAmount", new BigDecimal("1000.00"),
                                "targetCurrency", "INR"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void contributeEndpointReturnsPayment() throws Exception {
        Payment created = new Payment();
        created.setId(100L);

        when(campaignService.contributeToCampaign(eq(9L), any(CampaignContributionRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/campaigns/9/contribute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", 1,
                                "sourceAccountId", 5,
                                "amount", new BigDecimal("150.00"),
                                "currencyCode", "INR"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100));
    }
}

