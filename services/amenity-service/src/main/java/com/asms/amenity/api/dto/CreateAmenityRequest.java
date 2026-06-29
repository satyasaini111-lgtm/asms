package com.asms.amenity.api.dto;

import com.asms.amenity.domain.AmenityType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateAmenityRequest(
        @NotBlank String societyId,
        @NotBlank String name,
        @NotNull AmenityType type,
        @Min(1) int capacity,
        BigDecimal hourlyRate,
        String description,
        String operatingHours
) {}
