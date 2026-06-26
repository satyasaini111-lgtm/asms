package com.asms.billing.infrastructure;

import com.asms.billing.domain.Invoice;
import com.asms.billing.domain.InvoiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InvoiceRepository extends MongoRepository<Invoice, String> {
    Page<Invoice> findByUserId(String userId, Pageable pageable);
    Page<Invoice> findBySocietyId(String societyId, Pageable pageable);
    Page<Invoice> findByUserIdAndStatus(String userId, InvoiceStatus status, Pageable pageable);
}
