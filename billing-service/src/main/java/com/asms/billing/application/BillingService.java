package com.asms.billing.application;

import com.asms.billing.api.dto.GenerateInvoiceRequest;
import com.asms.billing.api.dto.InvoiceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BillingService {
    InvoiceResponse generate(GenerateInvoiceRequest request);
    InvoiceResponse getById(String id);
    Page<InvoiceResponse> listByUser(String userId, Pageable pageable);
    Page<InvoiceResponse> listBySociety(String societyId, Pageable pageable);
    InvoiceResponse markPaid(String id);
}
