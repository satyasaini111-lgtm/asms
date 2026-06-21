package com.asms.payment.infrastructure;

import com.asms.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {
    Page<Payment> findByUserId(String userId, Pageable pageable);
    Page<Payment> findBySocietyId(String societyId, Pageable pageable);
}
