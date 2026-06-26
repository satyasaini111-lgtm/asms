package com.asms.amenity.api;

import com.asms.amenity.api.dto.AmenityResponse;
import com.asms.amenity.api.dto.BookingResponse;
import com.asms.amenity.api.dto.CreateAmenityRequest;
import com.asms.amenity.api.dto.CreateBookingRequest;
import com.asms.amenity.application.AmenityService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AmenityController {

    private final AmenityService amenityService;

    public AmenityController(AmenityService amenityService) {
        this.amenityService = amenityService;
    }

    // ── Amenities ─────────────────────────────────────────────────────────────

    @PostMapping("/amenities")
    public ResponseEntity<AmenityResponse> create(@Valid @RequestBody CreateAmenityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(amenityService.create(request));
    }

    @GetMapping("/amenities/{id}")
    public ResponseEntity<AmenityResponse> getById(@PathVariable String id) {
        return ResponseEntity.ok(amenityService.getById(id));
    }

    @GetMapping("/amenities")
    public ResponseEntity<Page<AmenityResponse>> list(
            @RequestParam String societyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(amenityService.listBySociety(societyId, PageRequest.of(page, size)));
    }

    @PutMapping("/amenities/{id}")
    public ResponseEntity<AmenityResponse> update(
            @PathVariable String id,
            @Valid @RequestBody CreateAmenityRequest request) {
        return ResponseEntity.ok(amenityService.update(id, request));
    }

    @DeleteMapping("/amenities/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        amenityService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    // ── Bookings ───────────────────────────────────────────────────────────────

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(amenityService.createBooking(request));
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> getBookingById(@PathVariable String id) {
        return ResponseEntity.ok(amenityService.getBookingById(id));
    }

    @GetMapping("/bookings")
    public ResponseEntity<Page<BookingResponse>> listBookings(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(amenityService.listBookingsByUser(userId, PageRequest.of(page, size)));
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<BookingResponse> cancelBooking(@PathVariable String id) {
        return ResponseEntity.ok(amenityService.cancelBooking(id));
    }
}
