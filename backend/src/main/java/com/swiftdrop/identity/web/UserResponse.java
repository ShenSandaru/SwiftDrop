package com.swiftdrop.identity.web;

import java.util.List;
import java.util.UUID;

import com.swiftdrop.identity.domain.User;

public record UserResponse(UUID id, String email, String fullName, List<String> roles) {

    static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getRoles().stream().map(Enum::name).sorted().toList());
    }
}
