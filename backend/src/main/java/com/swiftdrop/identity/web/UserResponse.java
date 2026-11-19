package com.swiftdrop.identity.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.identity.domain.User;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        List<String> roles,
        boolean enabled,
        boolean hasAvatar,
        Instant avatarUpdatedAt,
        Instant createdAt,
        boolean profileCompleted,
        UUID hubId) {

    public UserResponse(UUID id, String email, String fullName, List<String> roles) {
        this(id, email, fullName, null, roles, true, false, null, null, true, null);
    }

    public UserResponse(UUID id, String email, String fullName, List<String> roles, boolean enabled) {
        this(id, email, fullName, null, roles, enabled, false, null, null, true, null);
    }

    public UserResponse(UUID id, String email, String fullName, List<String> roles, boolean enabled, Instant createdAt) {
        this(id, email, fullName, null, roles, enabled, false, null, createdAt, true, null);
    }

    public UserResponse(UUID id, String email, String fullName, String phoneNumber, List<String> roles, boolean enabled, Instant createdAt) {
        this(id, email, fullName, phoneNumber, roles, enabled, false, null, createdAt, true, null);
    }

    static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRoles().stream().map(Enum::name).sorted().toList(),
                user.isEnabled(),
                user.hasAvatar(),
                user.getAvatarUpdatedAt(),
                user.getCreatedAt(),
                user.isProfileCompleted(),
                user.getHubId());
    }
}
