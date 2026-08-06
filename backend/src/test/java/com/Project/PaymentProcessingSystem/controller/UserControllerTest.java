package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.Account;
import com.Project.PaymentProcessingSystem.model.AccountStatus;
import com.Project.PaymentProcessingSystem.model.CrowdfundingCampaign;
import com.Project.PaymentProcessingSystem.model.DashboardAnalyticsResponse;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.SupportTicket;
import com.Project.PaymentProcessingSystem.model.User;
import com.Project.PaymentProcessingSystem.service.AccountService;
import com.Project.PaymentProcessingSystem.service.CrowdfundingCampaignService;
import com.Project.PaymentProcessingSystem.service.PaymentService;
import com.Project.PaymentProcessingSystem.service.SupportTicketService;
import com.Project.PaymentProcessingSystem.service.UserService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @Mock
    private AccountService accountService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private SupportTicketService supportTicketService;

    @Mock
    private CrowdfundingCampaignService campaignService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(
                userService,
                accountService,
                paymentService,
                supportTicketService,
                campaignService
        )).build();
    }

    @Test
    void createUserReturnsCreated() throws Exception {
        User saved = new User();
        saved.setId(5L);
        saved.setFullName("Ragul Kumar");
        saved.setEmail("ragul@example.com");

        when(userService.createUser(any(User.class))).thenReturn(saved);

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", "Ragul Kumar", "email", "ragul@example.com"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void updateDailyLimitWithoutPayloadFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/users/1/daily-limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void workspaceEndpointReturnsAggregatedPayload() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setFullName("User One");

        Account account = new Account();
        account.setId(7L);
        account.setBalance(new BigDecimal("250.00"));
        account.setAccountStatus(AccountStatus.ACTIVE);

        Payment payment = new Payment();
        payment.setId(44L);

        SupportTicket ticket = new SupportTicket();
        ticket.setId(66L);

        CrowdfundingCampaign campaign = new CrowdfundingCampaign();
        campaign.setId(77L);

        DashboardAnalyticsResponse dashboard = new DashboardAnalyticsResponse();
        dashboard.setTotalPayments(3L);

        when(userService.getUserById(1L)).thenReturn(user);
        when(accountService.getAccountsByUserId(1L)).thenReturn(List.of(account));
        when(paymentService.getAllPayments(1L)).thenReturn(List.of(payment));
        when(supportTicketService.getTicketsByUserId(1L)).thenReturn(List.of(ticket));
        when(campaignService.getAllCampaigns()).thenReturn(List.of(campaign));
        when(paymentService.getDashboardAnalytics(1L)).thenReturn(dashboard);

        mockMvc.perform(get("/api/users/1/workspace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.accounts[0].id").value(7))
                .andExpect(jsonPath("$.payments[0].id").value(44))
                .andExpect(jsonPath("$.tickets[0].id").value(66))
                .andExpect(jsonPath("$.campaigns[0].id").value(77))
                .andExpect(jsonPath("$.dashboard.totalPayments").value(3));
    }
}

