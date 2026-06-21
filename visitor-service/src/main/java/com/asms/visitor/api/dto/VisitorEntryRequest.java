package com.asms.visitor.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VisitorEntryRequest(
        @NotBlank String visitorName,

        @NotBlank @Pattern(regexp = "^[+]?[0-9]{10,13}$")
        String visitorPhone,

        String vehicleNumber,
        String purpose,

        @NotBlank String societyId,
        @NotBlank String residentUserId
) {}
