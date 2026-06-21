package com.asms.user.api.dto;

import com.asms.user.domain.User;
import com.asms.user.domain.UserRole;
import com.asms.user.domain.UserStatus;

import java.time.Instant;

public record UserResponse(
        String id,
        String email,
        String firstName,
        String lastName,
        String phone,
        UserRole role,
        UserStatus status,
        String societyId,
        String unitId,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getSocietyId(),
                user.getUnitId(),
                user.getCreatedAt()
        );
    }
}
