package com.bunny.rtt_api.repository;

import com.bunny.rtt_api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    java.util.List<User> findAllByRoleNotOrderByCreatedAtDesc(String role);

    @org.springframework.data.jpa.repository.Query("SELECT u.email FROM User u WHERE u.email LIKE :pattern")
    java.util.List<String> findEmailsByPattern(@org.springframework.data.repository.query.Param("pattern") String pattern);
}
