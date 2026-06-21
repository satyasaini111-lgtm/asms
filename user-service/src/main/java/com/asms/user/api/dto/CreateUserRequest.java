package com.asms.user.api.dto;

import com.asms.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
        @NotBlank(message = "First name is required")
        String firstName,

        @NotBlank(message = "Last name is required")
        String lastName,

        @NotBlank @Email(message = "Valid email required")
        String email,

        @NotBlank @Pattern(regexp = "^[+]?[0-9]{10,13}$", message = "Valid phone required")
        String phone,

        @NotNull(message = "Role is required")
        UserRole role,

        @NotBlank(message = "Society ID is required")
        String societyId,

        String unitId
) {}
