package com.swiftdrop.identity.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only summary of an existing user account for other modules. Password hashes and JPA
 * entities are never exposed through this API.
 */
public record UserSummary(UUID id, String fullName, String email, Set<String> roles, boolean enabled) {

    public UserSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(email, "email");
        roles = roles != null ? Set.copyOf(roles) : Set.of();
    }

    public boolean hasRole(String roleName) {
        return roles.contains(roleName);
    }
}
