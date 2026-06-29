package com.asms.billing.api.dto;

import com.asms.billing.domain.InvoiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GenerateInvoiceRequest(
        @NotBlank String societyId,
        @NotBlank String userId,
        @NotBlank String unitId,
        @NotNull InvoiceType type,
        @NotBlank String billingPeriod
) {}
