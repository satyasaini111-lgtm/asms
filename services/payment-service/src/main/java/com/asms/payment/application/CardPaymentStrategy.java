package com.asms.payment.application;

import com.asms.payment.domain.Payment;
import com.asms.payment.domain.PaymentMethod;
import com.asms.payment.domain.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CardPaymentStrategy implements PaymentStrategy {

    private static final Logger log = LoggerFactory.getLogger(CardPaymentStrategy.class);

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.CARD;
    }

    @Override
    public Payment process(Payment payment) {
        log.info("Processing CARD payment: amount={} INR", payment.getAmount());
        payment.setTransactionId("CARD_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setGatewayResponse("{\"status\":\"SUCCESS\",\"authCode\":\"AUTH123\"}");
        return payment;
    }
}
