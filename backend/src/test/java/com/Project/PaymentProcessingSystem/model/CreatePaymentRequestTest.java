package com.Project.PaymentProcessingSystem.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreatePaymentRequestTest {

    @Test
    void dtoHoldsExtendedPaymentFields() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setUserId(1L);
        request.setSourceAccountId(10L);
        request.setDestinationAccountId(20L);
        request.setAmount(new BigDecimal("100.00"));
        request.setCurrencyCode("USD");
        request.setDestinationCurrencyCode("INR");
        request.setPaymentType(PaymentType.NORMAL_PAYMENT);
        request.setCrowdfundingCampaignId(99L);
        request.setIdempotencyKey("idem");
        request.setSourceAccountNumber("SRC-1");
        request.setDestinationAccountNumber("DST-1");
        request.setForexConfirmed(Boolean.TRUE);
        request.setConfirmationTimedOut(Boolean.FALSE);

        assertEquals(1L, request.getUserId());
        assertEquals(10L, request.getSourceAccountId());
        assertEquals(20L, request.getDestinationAccountId());
        assertEquals(new BigDecimal("100.00"), request.getAmount());
        assertEquals("USD", request.getCurrencyCode());
        assertEquals("INR", request.getDestinationCurrencyCode());
        assertEquals(PaymentType.NORMAL_PAYMENT, request.getPaymentType());
        assertEquals(99L, request.getCrowdfundingCampaignId());
        assertEquals("idem", request.getIdempotencyKey());
        assertEquals("SRC-1", request.getSourceAccountNumber());
        assertEquals("DST-1", request.getDestinationAccountNumber());
        assertTrue(request.getForexConfirmed());
    }
}

