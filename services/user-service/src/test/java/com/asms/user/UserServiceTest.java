package com.asms.user;

import com.asms.user.api.dto.CreateUserRequest;
import com.asms.user.api.dto.UserResponse;
import com.asms.user.application.UserServiceImpl;
import com.asms.user.domain.User;
import com.asms.user.domain.UserRole;
import com.asms.user.domain.UserStatus;
import com.asms.user.exception.DuplicateResourceException;
import com.asms.user.exception.ResourceNotFoundException;
import com.asms.user.infrastructure.kafka.UserEventPublisher;
import com.asms.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserEventPublisher eventPublisher;

    @InjectMocks
    private UserServiceImpl userService;

    private CreateUserRequest validRequest;
    private User savedUser;

    @BeforeEach
    void setUp() {
        validRequest = new CreateUserRequest(
                "Raj", "Sharma", "raj@example.com",
                "+919999999999", UserRole.OWNER,
                "soc_001", "unit_101"
        );
        savedUser = new User(
                "usr_abc123", "raj@example.com", "Raj", "Sharma",
                "+919999999999", UserRole.OWNER, UserStatus.ACTIVE,
                "soc_001", "unit_101", null
        );
    }

    @Test
    @DisplayName("createUser: success — saves and publishes event")
    void createUser_success() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse response = userService.createUser(validRequest);

        assertThat(response.email()).isEqualTo("raj@example.com");
        assertThat(response.role()).isEqualTo(UserRole.OWNER);
        verify(eventPublisher, times(1)).publishUserRegistered(any(User.class));
    }

    @Test
    @DisplayName("createUser: duplicate email — throws DuplicateResourceException")
    void createUser_duplicateEmail_throwsException() {
        when(userRepository.existsByEmail(anyString())).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser(validRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("raj@example.com");

        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserRegistered(any());
    }

    @Test
    @DisplayName("getUserById: existing id — returns user")
    void getUserById_found() {
        when(userRepository.findById("usr_abc123")).thenReturn(Optional.of(savedUser));

        UserResponse response = userService.getUserById("usr_abc123");

        assertThat(response.id()).isEqualTo("usr_abc123");
        assertThat(response.firstName()).isEqualTo("Raj");
    }

    @Test
    @DisplayName("getUserById: unknown id — throws ResourceNotFoundException")
    void getUserById_notFound() {
        when(userRepository.findById("usr_unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById("usr_unknown"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("usr_unknown");
    }

    @Test
    @DisplayName("deleteUser: existing user — deletes and publishes event")
    void deleteUser_success() {
        when(userRepository.existsById("usr_abc123")).thenReturn(true);

        userService.deleteUser("usr_abc123");

        verify(userRepository, times(1)).deleteById("usr_abc123");
        verify(eventPublisher, times(1)).publishUserDeleted("usr_abc123");
    }

    @Test
    @DisplayName("deleteUser: unknown user — throws ResourceNotFoundException")
    void deleteUser_notFound() {
        when(userRepository.existsById("usr_unknown")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteUser("usr_unknown"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).deleteById(anyString());
    }
}
