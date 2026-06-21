package com.asms.payment.application;

import com.asms.payment.domain.Payment;
import com.asms.payment.domain.PaymentMethod;
import com.asms.payment.domain.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NetBankingPaymentStrategy implements PaymentStrategy {

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.NET_BANKING;
    }

    @Override
    public Payment process(Payment payment) {
        payment.setTransactionId("NB_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setGatewayResponse("{\"status\":\"SUCCESS\",\"bankRef\":\"BANK123\"}");
        return payment;
    }
}
