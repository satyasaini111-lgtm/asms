package com.asms.payment.api.dto;

import com.asms.payment.domain.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record InitiatePaymentRequest(
        @NotNull @DecimalMin(value = "1.0", message = "Amount must be at least 1 INR")
        BigDecimal amount,

        @NotNull(message = "Payment method is required")
        PaymentMethod method,

        @NotBlank(message = "Society ID is required")
        String societyId,

        @NotBlank(message = "Reference ID is required")
        String referenceId,

        @NotBlank(message = "Reference type is required")
        String referenceType
) {}
