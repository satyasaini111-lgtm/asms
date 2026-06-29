package com.asms.user.api.dto;

import com.asms.user.domain.UserStatus;
import jakarta.validation.constraints.Pattern;

public record UpdateUserRequest(
        String firstName,
        String lastName,

        @Pattern(regexp = "^[+]?[0-9]{10,13}$", message = "Valid phone required")
        String phone,

        UserStatus status,
        String unitId
) {}
