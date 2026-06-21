package com.asms.payment.application;

import com.asms.payment.domain.Payment;
import com.asms.payment.domain.PaymentMethod;
import com.asms.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UpiPaymentStrategy implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(UpiPaymentStrategy.class);

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.UPI;
    }

    @Override
    public Payment process(Payment payment) {
        log.info("Processing UPI payment: amount={} INR for userId={}", payment.getAmount(), payment.getUserId());
        // Mock: simulate success
        payment.setTransactionId("UPI_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setGatewayResponse("{\"status\":\"SUCCESS\",\"upiRefId\":\"" + payment.getTransactionId() + "\"}");
        return payment;
    }
}
