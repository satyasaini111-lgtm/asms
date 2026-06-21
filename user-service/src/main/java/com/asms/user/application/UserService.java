package com.asms.user.application;

import com.asms.user.api.dto.CreateUserRequest;
import com.asms.user.api.dto.UpdateUserRequest;
import com.asms.user.api.dto.UserResponse;
import com.asms.user.domain.UserRole;
import com.asms.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    UserResponse getUserById(String id);
    UserResponse getUserByEmail(String email);
    Page<UserResponse> listUsers(String societyId, UserRole role, UserStatus status, Pageable pageable);
    UserResponse updateUser(String id, UpdateUserRequest request);
    void deleteUser(String id);
}
