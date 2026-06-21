package com.asms.payment.api.controller;

import com.asms.payment.api.dto.InitiatePaymentRequest;
import com.asms.payment.api.dto.PaymentResponse;
import com.asms.payment.application.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment processing operations")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
    @Operation(summary = "Initiate a payment")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @RequestHeader("X-User-Id") String userId) {
        PaymentResponse response = paymentService.initiatePayment(request, userId);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RWA_MEMBER', 'OWNER', 'TENANT')")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable String id) {
        return ResponseEntity.ok(paymentService.getPaymentById(id));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('OWNER', 'TENANT')")
    @Operation(summary = "Get my payments")
    public ResponseEntity<Page<PaymentResponse>> getMyPayments(
            @RequestHeader("X-User-Id") String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(paymentService.getPaymentsByUser(userId, pageable));
    }
}
