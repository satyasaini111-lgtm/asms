package com.asms.amenity.api.dto;

import com.asms.amenity.domain.Amenity;
import com.asms.amenity.domain.AmenityType;

import java.math.BigDecimal;

public record AmenityResponse(
        String id,
        String societyId,
        String name,
        AmenityType type,
        int capacity,
        BigDecimal hourlyRate,
        boolean active,
        String description,
        String operatingHours
) {
    public static AmenityResponse from(Amenity a) {
        return new AmenityResponse(a.getId(), a.getSocietyId(), a.getName(), a.getType(),
                a.getCapacity(), a.getHourlyRate(), a.isActive(), a.getDescription(), a.getOperatingHours());
    }
}
