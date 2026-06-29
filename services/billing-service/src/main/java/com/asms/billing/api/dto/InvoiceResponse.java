package com.asms.billing.api.dto;

import com.asms.billing.domain.Invoice;
import com.asms.billing.domain.InvoiceStatus;
import com.asms.billing.domain.InvoiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
        String id,
        String societyId,
        String userId,
        String unitId,
        InvoiceType type,
        InvoiceStatus status,
        BigDecimal totalAmount,
        String currency,
        String billingPeriod,
        String dueDate,
        List<LineItemDto> lineItems,
        Instant createdAt
) {
    public record LineItemDto(String description, BigDecimal amount, int quantity, BigDecimal subtotal) {}

    public static InvoiceResponse from(Invoice inv) {
        List<LineItemDto> items = inv.getLineItems().stream()
                .map(li -> new LineItemDto(li.description(), li.amount(), li.quantity(), li.subtotal()))
                .toList();
        return new InvoiceResponse(inv.getId(), inv.getSocietyId(), inv.getUserId(), inv.getUnitId(),
                inv.getType(), inv.getStatus(), inv.getTotalAmount(), inv.getCurrency(),
                inv.getBillingPeriod(), inv.getDueDate(), items, inv.getCreatedAt());
    }
}
