package com.asms.billing.application;

import com.asms.billing.api.dto.GenerateInvoiceRequest;
import com.asms.billing.api.dto.InvoiceResponse;
import com.asms.billing.domain.Invoice;
import com.asms.billing.domain.InvoiceStatus;
import com.asms.billing.domain.InvoiceType;
import com.asms.billing.exception.ResourceNotFoundException;
import com.asms.billing.infrastructure.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class BillingServiceImpl implements BillingService {

    private final InvoiceRepository invoiceRepository;
    private final RecurringMaintenanceInvoiceGenerator recurringGenerator;
    private final AdHocInvoiceGenerator adHocGenerator;

    public BillingServiceImpl(InvoiceRepository invoiceRepository,
                               RecurringMaintenanceInvoiceGenerator recurringGenerator,
                               AdHocInvoiceGenerator adHocGenerator) {
        this.invoiceRepository = invoiceRepository;
        this.recurringGenerator = recurringGenerator;
        this.adHocGenerator = adHocGenerator;
    }

    @Override
    public InvoiceResponse generate(GenerateInvoiceRequest req) {
        AbstractInvoiceGenerator generator = req.type() == InvoiceType.MONTHLY_MAINTENANCE
                ? recurringGenerator : adHocGenerator;
        Invoice invoice = generator.generate(req.societyId(), req.userId(), req.unitId(), req.billingPeriod());
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    @Override
    public InvoiceResponse getById(String id) {
        return InvoiceResponse.from(invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id)));
    }

    @Override
    public Page<InvoiceResponse> listByUser(String userId, Pageable pageable) {
        return invoiceRepository.findByUserId(userId, pageable).map(InvoiceResponse::from);
    }

    @Override
    public Page<InvoiceResponse> listBySociety(String societyId, Pageable pageable) {
        return invoiceRepository.findBySocietyId(societyId, pageable).map(InvoiceResponse::from);
    }

    @Override
    public InvoiceResponse markPaid(String id) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
        invoice.setStatus(InvoiceStatus.PAID);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }
}
