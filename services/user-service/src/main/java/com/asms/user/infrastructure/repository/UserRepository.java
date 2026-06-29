package com.asms.user.infrastructure.repository;

import com.asms.user.domain.User;
import com.asms.user.domain.UserRole;
import com.asms.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Page<User> findBySocietyId(String societyId, Pageable pageable);
    Page<User> findBySocietyIdAndRole(String societyId, UserRole role, Pageable pageable);
    Page<User> findBySocietyIdAndStatus(String societyId, UserStatus status, Pageable pageable);
}
