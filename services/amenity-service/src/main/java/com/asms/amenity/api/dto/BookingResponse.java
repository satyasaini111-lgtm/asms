package com.asms.amenity.api.dto;

import com.asms.amenity.domain.Booking;
import com.asms.amenity.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingResponse(
        String id,
        String amenityId,
        String userId,
        String societyId,
        Instant slotStart,
        Instant slotEnd,
        BookingStatus status,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public static BookingResponse from(Booking b) {
        return new BookingResponse(b.getId(), b.getAmenityId(), b.getUserId(), b.getSocietyId(),
                b.getSlotStart(), b.getSlotEnd(), b.getStatus(), b.getTotalAmount(), b.getCreatedAt());
    }
}
