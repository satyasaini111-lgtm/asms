package com.asms.amenity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateBookingRequest(
        @NotBlank String amenityId,
        @NotBlank String userId,
        @NotBlank String societyId,
        @NotNull Instant slotStart,
        @NotNull Instant slotEnd
) {}
