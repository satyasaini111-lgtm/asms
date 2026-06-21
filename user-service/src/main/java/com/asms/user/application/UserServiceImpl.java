package com.asms.user.application;

import com.asms.user.api.dto.CreateUserRequest;
import com.asms.user.api.dto.UpdateUserRequest;
import com.asms.user.api.dto.UserResponse;
import com.asms.user.domain.User;
import com.asms.user.domain.UserRole;
import com.asms.user.domain.UserStatus;
import com.asms.user.exception.DuplicateResourceException;
import com.asms.user.exception.ResourceNotFoundException;
import com.asms.user.infrastructure.kafka.UserEventPublisher;
import com.asms.user.infrastructure.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final UserEventPublisher eventPublisher;

    public UserServiceImpl(UserRepository userRepository, UserEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("User with email already exists: " + request.email());
        }

        User user = new User(
                "usr_" + UUID.randomUUID().toString().replace("-", ""),
                request.email(),
                request.firstName(),
                request.lastName(),
                request.phone(),
                request.role(),
                UserStatus.ACTIVE,
                request.societyId(),
                request.unitId(),
                null
        );

        User saved = userRepository.save(user);
        log.info("User registered: {}", saved.getId());
        eventPublisher.publishUserRegistered(saved);
        return UserResponse.from(saved);
    }

    @Override
    @Cacheable(value = "users", key = "#id")
    public UserResponse getUserById(String id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Override
    @Cacheable(value = "users", key = "'email:' + #email")
    public UserResponse getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    public Page<UserResponse> listUsers(String societyId, UserRole role, UserStatus status, Pageable pageable) {
        if (role != null) {
            return userRepository.findBySocietyIdAndRole(societyId, role, pageable)
                    .map(UserResponse::from);
        }
        if (status != null) {
            return userRepository.findBySocietyIdAndStatus(societyId, status, pageable)
                    .map(UserResponse::from);
        }
        return userRepository.findBySocietyId(societyId, pageable).map(UserResponse::from);
    }

    @Override
    @CacheEvict(value = "users", key = "#id")
    public UserResponse updateUser(String id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        if (request.firstName() != null) user.setFirstName(request.firstName());
        if (request.lastName() != null) user.setLastName(request.lastName());
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.status() != null) user.setStatus(request.status());
        if (request.unitId() != null) user.setUnitId(request.unitId());

        User saved = userRepository.save(user);
        eventPublisher.publishUserUpdated(saved);
        return UserResponse.from(saved);
    }

    @Override
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(String id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        userRepository.deleteById(id);
        eventPublisher.publishUserDeleted(id);
        log.info("User deleted: {}", id);
    }
}
