package com.asms.billing.application;

import com.asms.billing.domain.InvoiceLineItem;
import com.asms.billing.domain.InvoiceType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AdHocInvoiceGenerator extends AbstractInvoiceGenerator {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AdHocInvoiceGenerator(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    protected InvoiceType invoiceType() {
        return InvoiceType.AD_HOC;
    }

    @Override
    protected List<InvoiceLineItem> collectLineItems(String societyId, String userId,
                                                     String unitId, String billingPeriod) {
        // Ad-hoc: minimal single line item; real caller would pass items via context
        return List.of(new InvoiceLineItem("Ad-Hoc Charge", BigDecimal.ZERO, 1));
    }

    @Override
    protected String computeDueDate(String billingPeriod) {
        return billingPeriod + "-15";
    }
}
