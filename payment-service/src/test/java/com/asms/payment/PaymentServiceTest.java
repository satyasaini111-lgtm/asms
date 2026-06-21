package com.asms.payment;

import com.asms.payment.api.dto.InitiatePaymentRequest;
import com.asms.payment.api.dto.PaymentResponse;
import com.asms.payment.application.*;
import com.asms.payment.domain.Payment;
import com.asms.payment.domain.PaymentMethod;
import com.asms.payment.domain.PaymentStatus;
import com.asms.payment.infrastructure.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — Strategy Pattern Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        List<PaymentStrategy> strategies = List.of(
                new UpiPaymentStrategy(),
                new CardPaymentStrategy(),
                new NetBankingPaymentStrategy()
        );
        paymentService = new PaymentServiceImpl(paymentRepository, strategies, kafkaTemplate);
    }

    @Test
    @DisplayName("UPI payment: processed successfully")
    void processUpiPayment() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        PaymentResponse response = paymentService.initiatePayment(
                new InitiatePaymentRequest(new BigDecimal("5000"), PaymentMethod.UPI,
                        "soc_001", "inv_001", "INVOICE"),
                "usr_001"
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.method()).isEqualTo(PaymentMethod.UPI);
        assertThat(response.transactionId()).startsWith("UPI_");
    }

    @Test
    @DisplayName("CARD payment: processed successfully")
    void processCardPayment() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        PaymentResponse response = paymentService.initiatePayment(
                new InitiatePaymentRequest(new BigDecimal("2500"), PaymentMethod.CARD,
                        "soc_001", "bkg_001", "BOOKING"),
                "usr_002"
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.transactionId()).startsWith("CARD_");
    }

    @Test
    @DisplayName("WALLET payment: unsupported method throws exception")
    void processWalletPayment_unsupported() {
        assertThatThrownBy(() -> paymentService.initiatePayment(
                new InitiatePaymentRequest(new BigDecimal("1000"), PaymentMethod.WALLET,
                        "soc_001", "inv_002", "INVOICE"),
                "usr_003"
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported payment method");
    }

    @Test
    @DisplayName("NET_BANKING payment: delegates to correct strategy")
    void processNetBankingPayment() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));

        PaymentResponse response = paymentService.initiatePayment(
                new InitiatePaymentRequest(new BigDecimal("8000"), PaymentMethod.NET_BANKING,
                        "soc_001", "inv_003", "INVOICE"),
                "usr_004"
        );

        assertThat(response.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(response.transactionId()).startsWith("NB_");
    }
}
