package com.asms.payment.api.dto;

import com.asms.payment.domain.Payment;
import com.asms.payment.domain.PaymentMethod;
import com.asms.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        String id,
        String userId,
        String societyId,
        String referenceId,
        String referenceType,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String transactionId,
        Instant createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getUserId(),
                payment.getSocietyId(),
                payment.getReferenceId(),
                payment.getReferenceType(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getCreatedAt()
        );
    }
}
