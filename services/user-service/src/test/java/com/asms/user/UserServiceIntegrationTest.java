package com.asms.user;

import com.asms.user.api.dto.CreateUserRequest;
import com.asms.user.api.dto.UserResponse;
import com.asms.user.domain.UserRole;
import com.asms.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("UserService Integration Tests (Testcontainers)")
class UserServiceIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:7.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        // Point Kafka at a non-existent broker; KafkaTemplate send is async (fire-and-forget)
        // so integration tests complete without a real broker
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19999");
        registry.add("spring.kafka.producer.retries", () -> "0");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/users — 201 Created with valid request")
    void createUser_returns201() {
        CreateUserRequest request = new CreateUserRequest(
                "Test", "User", "test.integration@asms.com",
                "+919000000001", UserRole.OWNER, "soc_integration", "unit_001"
        );

        ResponseEntity<UserResponse> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/users",
                request,
                UserResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("test.integration@asms.com");
        assertThat(response.getBody().id()).startsWith("usr_");

        // Verify persisted in MongoDB
        assertThat(userRepository.findByEmail("test.integration@asms.com")).isPresent();
    }

    @Test
    @DisplayName("POST /api/v1/users — 422 on missing required fields")
    void createUser_returns422_onValidationFailure() {
        CreateUserRequest invalidRequest = new CreateUserRequest(
                "", "", "not-an-email",
                "123", null, "", null
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/users",
                invalidRequest,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    @DisplayName("POST /api/v1/users — 409 on duplicate email")
    void createUser_returns409_onDuplicateEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "Dup", "User", "dup@asms.com",
                "+919000000002", UserRole.TENANT, "soc_integration", null
        );

        restTemplate.postForEntity("http://localhost:" + port + "/api/v1/users", request, UserResponse.class);
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/v1/users", request, String.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
