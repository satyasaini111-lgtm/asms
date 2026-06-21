package com.asms.billing.application;

import com.asms.billing.domain.Invoice;
import com.asms.billing.domain.InvoiceLineItem;
import com.asms.billing.domain.InvoiceStatus;
import com.asms.billing.domain.InvoiceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class RecurringMaintenanceInvoiceGenerator extends AbstractInvoiceGenerator {

    private static final Logger log = LoggerFactory.getLogger(RecurringMaintenanceInvoiceGenerator.class);
    private static final BigDecimal BASE_MAINTENANCE = new BigDecimal("2500");
    private static final BigDecimal WATER_CHARGE = new BigDecimal("500");
    private static final BigDecimal COMMON_AREA_CHARGE = new BigDecimal("300");

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public RecurringMaintenanceInvoiceGenerator(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    protected InvoiceType invoiceType() {
        return InvoiceType.MONTHLY_MAINTENANCE;
    }

    @Override
    protected List<InvoiceLineItem> collectLineItems(String societyId, String userId,
                                                     String unitId, String billingPeriod) {
        return List.of(
                new InvoiceLineItem("Monthly Maintenance", BASE_MAINTENANCE, 1),
                new InvoiceLineItem("Water Charges", WATER_CHARGE, 1),
                new InvoiceLineItem("Common Area Maintenance", COMMON_AREA_CHARGE, 1)
        );
    }

    @Override
    protected String computeDueDate(String billingPeriod) {
        // Due on the 10th of billing month: e.g., "2024-01" → "2024-01-10"
        return billingPeriod + "-10";
    }

    @Override
    protected void onGenerated(Invoice invoice) {
        invoice.setStatus(InvoiceStatus.ISSUED);
        kafkaTemplate.send("invoice.issued", invoice.getId(), Map.of(
                "eventType", "INVOICE_ISSUED",
                "invoiceId", invoice.getId(),
                "userId", invoice.getUserId(),
                "amount", invoice.getTotalAmount(),
                "dueDate", invoice.getDueDate()
        ));
        log.info("Monthly maintenance invoice issued: {} for userId={}", invoice.getId(), invoice.getUserId());
    }
}
