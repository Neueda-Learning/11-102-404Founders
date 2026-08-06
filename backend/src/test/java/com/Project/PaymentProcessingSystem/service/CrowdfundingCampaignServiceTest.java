package com.Project.PaymentProcessingSystem.service;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CampaignContributionRequest;
import com.Project.PaymentProcessingSystem.model.CampaignStatus;
import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.repository.CrowdfundingCampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrowdfundingCampaignServiceTest {

    @Mock
    private CrowdfundingCampaignRepository campaignRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private CrowdfundingCampaignService campaignService;

    @Test
    void createCampaignRejectsPastDeadline() {
        CrowdfundingCampaign campaign = new CrowdfundingCampaign();
        campaign.setCampaignName("Health Fund");
        campaign.setBucketAccountId(11L);
        campaign.setTargetAmount(new BigDecimal("1000.00"));
        campaign.setCampaignEndDate(LocalDate.now().minusDays(1));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> campaignService.createCampaign(campaign));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Campaign deadline must be today or a future date", ex.getReason());
    }

    @Test
    void createCampaignSetsDefaultsAndSaves() {
        CrowdfundingCampaign campaign = new CrowdfundingCampaign();
        campaign.setCampaignName("Education Drive");
        campaign.setBucketAccountId(12L);
        campaign.setTargetAmount(new BigDecimal("5000.00"));
        campaign.setTargetCurrency("usd");
        campaign.setCampaignEndDate(LocalDate.now().plusDays(10));

        Account bucket = new Account();
        bucket.setId(12L);
        bucket.setAccountStatus(AccountStatus.ACTIVE);
        bucket.setCurrencyCode("USD");

        Account createdBucket = new Account();
        createdBucket.setId(1200L);

        when(accountService.getAccountById(12L)).thenReturn(bucket);
        when(accountService.createAccount(org.mockito.ArgumentMatchers.any(Account.class))).thenReturn(createdBucket);
        when(campaignRepository.save(org.mockito.ArgumentMatchers.any(CrowdfundingCampaign.class))).thenAnswer(invocation -> {
            CrowdfundingCampaign saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        CrowdfundingCampaign result = campaignService.createCampaign(campaign);

        assertEquals(99L, result.getId());
        assertEquals(CampaignStatus.ACTIVE, result.getStatus());
        assertEquals("USD", result.getTargetCurrency());
        assertEquals(BigDecimal.ZERO, result.getCurrentAmount());
        assertEquals(1200L, result.getBucketAccountId());
    }

    @Test
    void contributeToCampaignBuildsCrowdfundingPaymentRequest() {
        CrowdfundingCampaign campaign = new CrowdfundingCampaign();
        campaign.setId(7L);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setCampaignEndDate(LocalDate.now().plusDays(5));
        campaign.setCurrentAmount(new BigDecimal("100.00"));
        campaign.setTargetAmount(new BigDecimal("1000.00"));
        campaign.setBucketAccountId(200L);
        campaign.setTargetCurrency("INR");

        when(campaignRepository.findById(7L)).thenReturn(Optional.of(campaign));

        Payment created = new Payment();
        created.setId(500L);
        created.setPaymentType(PaymentType.CROWDFUNDING_PAYMENT);
        when(paymentService.createPayment(org.mockito.ArgumentMatchers.any(CreatePaymentRequest.class))).thenReturn(created);

        CampaignContributionRequest request = new CampaignContributionRequest();
        request.setUserId(3L);
        request.setSourceAccountId(21L);
        request.setAmount(new BigDecimal("250.00"));
        request.setCurrencyCode("INR");
        request.setIdempotencyKey("campaign-contrib-key");

        Payment result = campaignService.contributeToCampaign(7L, request);

        assertEquals(500L, result.getId());
        ArgumentCaptor<CreatePaymentRequest> captor = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(paymentService).createPayment(captor.capture());
        CreatePaymentRequest payload = captor.getValue();
        assertEquals(3L, payload.getUserId());
        assertEquals(21L, payload.getSourceAccountId());
        assertEquals(200L, payload.getDestinationAccountId());
        assertEquals(PaymentType.CROWDFUNDING_PAYMENT, payload.getPaymentType());
        assertEquals(7L, payload.getCrowdfundingCampaignId());
        assertEquals("INR", payload.getDestinationCurrencyCode());
    }
}

