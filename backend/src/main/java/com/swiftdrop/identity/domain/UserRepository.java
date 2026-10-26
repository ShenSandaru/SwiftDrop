package com.swiftdrop.identity.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Looks up a user by an email address in any case. */
    default Optional<User> findByEmailIgnoringCase(String email) {
        return findByEmail(User.normalizeEmail(email));
    }

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByRoles(Role role);

    @Query(value = "SELECT DISTINCT u FROM User u JOIN u.roles r WHERE r = :role",
           countQuery = "SELECT count(DISTINCT u) FROM User u JOIN u.roles r WHERE r = :role")
    Page<User> findByRole(@Param("role") Role role, Pageable pageable);
}
