package com.swiftdrop.notification.web;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.notification.application.PushTokenService;
import com.swiftdrop.notification.domain.NotificationPushToken;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * REST controller for registering and unregistering FCM push tokens
 * for the authenticated user.
 */
@RestController
@RequestMapping("/api/v1/notifications/push-tokens")
@PreAuthorize("isAuthenticated()")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    public PushTokenController(PushTokenService pushTokenService) {
        this.pushTokenService = Objects.requireNonNull(pushTokenService, "pushTokenService must not be null");
    }

    @PostMapping
    public PushTokenResponse register(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RegisterPushTokenRequest request) {
        UUID userId = Callers.userId(jwt);
        NotificationPushToken saved = pushTokenService.registerToken(
                userId, request.token(), request.platform());
        return PushTokenResponse.from(saved);
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UnregisterPushTokenRequest request) {
        UUID userId = Callers.userId(jwt);
        pushTokenService.unregisterToken(userId, request.token());
        return ResponseEntity.noContent().build();
    }

    public record RegisterPushTokenRequest(
            @NotBlank(message = "token is required") String token,
            @Size(max = 20, message = "platform must not exceed 20 characters") String platform) {
    }

    public record UnregisterPushTokenRequest(
            @NotBlank(message = "token is required") String token) {
    }

    public record PushTokenResponse(
            UUID id,
            String platform,
            boolean active,
            Instant updatedAt) {

        static PushTokenResponse from(NotificationPushToken entity) {
            return new PushTokenResponse(
                    entity.getId(),
                    entity.getPlatform(),
                    entity.isActive(),
                    entity.getUpdatedAt());
        }
    }
}
