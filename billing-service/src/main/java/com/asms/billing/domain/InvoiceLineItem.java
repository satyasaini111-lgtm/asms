package com.asms.billing.domain;

import java.math.BigDecimal;

public record InvoiceLineItem(String description, BigDecimal amount, int quantity) {
    public BigDecimal subtotal() {
        return amount.multiply(BigDecimal.valueOf(quantity));
    }
}
