package com.Project.PaymentProcessingSystem.controller;

import com.Project.PaymentProcessingSystem.model.CreatePaymentRequest;
import com.Project.PaymentProcessingSystem.model.Payment;
import com.Project.PaymentProcessingSystem.model.PaymentReversalRequest;
import com.Project.PaymentProcessingSystem.model.PaymentStatus;
import com.Project.PaymentProcessingSystem.model.PaymentType;
import com.Project.PaymentProcessingSystem.service.PaymentService;
import com.Project.PaymentProcessingSystem.service.SupportTicketService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PaymentService paymentService;

    @Mock
    private SupportTicketService supportTicketService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService, supportTicketService)).build();
    }

    @Test
    void getAllPaymentsReturnsWorkspacePayments() throws Exception {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setPaymentReference("PAY-123");
        payment.setStatus(PaymentStatus.COMPLETED);

        when(paymentService.findPaymentsForWorkspace(eq(11L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(payment));

        mockMvc.perform(get("/api/payments")
                        .param("userId", "11")
                        .param("status", "COMPLETED")
                        .param("sortBy", "date")
                        .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].paymentReference").value("PAY-123"));
    }

    @Test
    void createPaymentReturnsCreated() throws Exception {
        Payment created = new Payment();
        created.setId(88L);
        created.setStatus(PaymentStatus.COMPLETED);
        created.setPaymentType(PaymentType.NORMAL_PAYMENT);

        when(paymentService.createPayment(any(CreatePaymentRequest.class))).thenReturn(created);

        Map<String, Object> body = Map.of(
                "userId", 9,
                "sourceAccountId", 1,
                "destinationAccountId", 2,
                "amount", new BigDecimal("100.00"),
                "currencyCode", "INR",
                "destinationCurrencyCode", "INR",
                "paymentType", "NORMAL_PAYMENT"
        );

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(88));
    }

    @Test
    void updateStatusWithoutStatusFieldReturnsBadRequest() throws Exception {
        mockMvc.perform(patch("/api/payments/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "manual"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reversePaymentReturnsCreated() throws Exception {
        Payment reversal = new Payment();
        reversal.setId(501L);
        reversal.setStatus(PaymentStatus.REVERSED);

        when(paymentService.reverseReceivedPayment(eq(55L), any(PaymentReversalRequest.class))).thenReturn(reversal);

        mockMvc.perform(post("/api/payments/55/reverse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("userId", 12, "reason", "refund"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REVERSED"));
    }
}

