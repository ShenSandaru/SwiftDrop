package com.swiftdrop.identity.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Looks up a user by an email address in any case. */
    default Optional<User> findByEmailIgnoringCase(String email) {
        return findByEmail(User.normalizeEmail(email));
    }

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByRoles(Role role);
}
