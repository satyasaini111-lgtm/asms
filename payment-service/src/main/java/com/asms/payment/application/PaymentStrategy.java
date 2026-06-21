package com.asms.payment.application;

import com.asms.payment.domain.Payment;
import com.asms.payment.domain.PaymentMethod;

// Strategy Pattern — each payment method implements this interface
public interface PaymentStrategy {
    PaymentMethod supports();
    Payment process(Payment payment);
}
