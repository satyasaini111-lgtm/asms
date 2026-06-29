package com.asms.billing.application;

import com.asms.billing.domain.Invoice;
import com.asms.billing.domain.InvoiceLineItem;
import com.asms.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Template Method Pattern — defines the invoice generation algorithm skeleton.
 * Subclasses override the steps (collectLineItems, computeDueDate, onGenerated).
 */
public abstract class AbstractInvoiceGenerator {

    public final Invoice generate(String societyId, String userId, String unitId, String billingPeriod) {
        Invoice invoice = new Invoice();
        invoice.setId("inv_" + java.util.UUID.randomUUID().toString().replace("-", ""));
        invoice.setSocietyId(societyId);
        invoice.setUserId(userId);
        invoice.setUnitId(unitId);
        invoice.setBillingPeriod(billingPeriod);
        invoice.setType(invoiceType());
        invoice.setStatus(InvoiceStatus.DRAFT);

        List<InvoiceLineItem> lineItems = collectLineItems(societyId, userId, unitId, billingPeriod);
        invoice.setLineItems(lineItems);
        invoice.setTotalAmount(calculateTotal(lineItems));
        invoice.setDueDate(computeDueDate(billingPeriod));

        onGenerated(invoice);
        return invoice;
    }

    protected abstract com.asms.billing.domain.InvoiceType invoiceType();
    protected abstract List<InvoiceLineItem> collectLineItems(String societyId, String userId,
                                                               String unitId, String billingPeriod);
    protected abstract String computeDueDate(String billingPeriod);

    protected void onGenerated(Invoice invoice) {
        // hook — subclasses may override for notifications or audit
    }

    private BigDecimal calculateTotal(List<InvoiceLineItem> items) {
        return items.stream()
                .map(InvoiceLineItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
