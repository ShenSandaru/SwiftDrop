package com.swiftdrop.notification.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.notification.domain.NotificationPushToken;
import com.swiftdrop.notification.domain.NotificationPushTokenRepository;

/**
 * Application service for registering and deactivating user FCM push tokens.
 */
@Service
@ConditionalOnBean(NotificationPushTokenRepository.class)
public class PushTokenService {

    private final NotificationPushTokenRepository pushTokenRepository;

    public PushTokenService(NotificationPushTokenRepository pushTokenRepository) {
        this.pushTokenRepository = Objects.requireNonNull(
                pushTokenRepository, "pushTokenRepository must not be null");
    }

    @Transactional
    public NotificationPushToken registerToken(UUID userId, String token, String platform) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null or blank");
        }

        Instant now = Instant.now();
        return pushTokenRepository.findByToken(token)
                .map(existing -> {
                    existing.reassignToUser(userId, platform, now);
                    return pushTokenRepository.save(existing);
                })
                .orElseGet(() -> pushTokenRepository.save(
                        new NotificationPushToken(userId, token, platform, now)));
    }

    @Transactional
    public boolean unregisterToken(UUID userId, String token) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be null or blank");
        }
        return pushTokenRepository.deactivateTokenForUser(token, userId, Instant.now()) > 0;
    }
}
