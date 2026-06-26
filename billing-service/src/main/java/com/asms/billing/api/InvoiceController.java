package com.asms.billing.api;

import com.asms.billing.api.dto.GenerateInvoiceRequest;
import com.asms.billing.api.dto.InvoiceResponse;
import com.asms.billing.application.BillingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final BillingService billingService;

    public InvoiceController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/generate")
    public ResponseEntity<InvoiceResponse> generate(@Valid @RequestBody GenerateInvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(billingService.generate(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(billingService.getById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<InvoiceResponse>> listByUser(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(billingService.listByUser(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/society/{societyId}")
    public ResponseEntity<Page<InvoiceResponse>> listBySociety(
            @PathVariable String societyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(billingService.listBySociety(societyId, PageRequest.of(page, size)));
    }

    @PatchMapping("/{id}/pay")
    public ResponseEntity<InvoiceResponse> markPaid(@PathVariable String id) {
        return ResponseEntity.ok(billingService.markPaid(id));
    }
}
