package com.asms.payment.application;

import com.asms.payment.api.dto.InitiatePaymentRequest;
import com.asms.payment.api.dto.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
    PaymentResponse initiatePayment(InitiatePaymentRequest request, String userId);
    PaymentResponse getPaymentById(String id);
    Page<PaymentResponse> getPaymentsByUser(String userId, Pageable pageable);
}
