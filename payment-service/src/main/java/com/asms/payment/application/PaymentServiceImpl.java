package com.asms.payment.application;

import com.asms.payment.api.dto.InitiatePaymentRequest;
import com.asms.payment.api.dto.PaymentResponse;
import com.asms.payment.domain.Payment;
import com.asms.payment.domain.PaymentStatus;
import com.asms.payment.exception.ResourceNotFoundException;
import com.asms.payment.infrastructure.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final Map<com.asms.payment.domain.PaymentMethod, PaymentStrategy> strategies;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Spring auto-discovers all PaymentStrategy beans via the List injection
    public PaymentServiceImpl(PaymentRepository paymentRepository,
                               List<PaymentStrategy> strategyList,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentRepository = paymentRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(PaymentStrategy::supports, Function.identity()));
    }

    @Override
    public PaymentResponse initiatePayment(InitiatePaymentRequest request, String userId) {
        PaymentStrategy strategy = strategies.get(request.method());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported payment method: " + request.method());
        }

        Payment payment = new Payment();
        payment.setId("pay_" + UUID.randomUUID().toString().replace("-", ""));
        payment.setUserId(userId);
        payment.setSocietyId(request.societyId());
        payment.setReferenceId(request.referenceId());
        payment.setReferenceType(request.referenceType());
        payment.setAmount(request.amount());
        payment.setMethod(request.method());
        payment.setStatus(PaymentStatus.INITIATED);

        Payment processed = strategy.process(payment);
        Payment saved = paymentRepository.save(processed);

        if (saved.getStatus() == PaymentStatus.COMPLETED) {
            publishPaymentCompleted(saved);
        }

        log.info("Payment processed: id={} status={}", saved.getId(), saved.getStatus());
        return PaymentResponse.from(saved);
    }

    @Override
    public PaymentResponse getPaymentById(String id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }

    @Override
    public Page<PaymentResponse> getPaymentsByUser(String userId, Pageable pageable) {
        return paymentRepository.findByUserId(userId, pageable).map(PaymentResponse::from);
    }

    private void publishPaymentCompleted(Payment payment) {
        Map<String, Object> event = Map.of(
                "eventType", "PAYMENT_COMPLETED",
                "paymentId", payment.getId(),
                "userId", payment.getUserId(),
                "amount", payment.getAmount(),
                "referenceId", payment.getReferenceId(),
                "referenceType", payment.getReferenceType()
        );
        kafkaTemplate.send("payment.completed", payment.getId(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish PAYMENT_COMPLETED: {}", ex.getMessage());
                });
    }
}
