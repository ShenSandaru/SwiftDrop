package com.swiftdrop.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationPushTokenTest {

    @Test
    @DisplayName("Creates active WEB push token with timestamps")
    void createsActiveWebToken() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-16T08:00:00Z");

        NotificationPushToken pushToken = new NotificationPushToken(userId, "fcm-token-123", null, now);

        assertThat(pushToken.getUserId()).isEqualTo(userId);
        assertThat(pushToken.getToken()).isEqualTo("fcm-token-123");
        assertThat(pushToken.getPlatform()).isEqualTo("WEB");
        assertThat(pushToken.isActive()).isTrue();
        assertThat(pushToken.getCreatedAt()).isEqualTo(now);
        assertThat(pushToken.getUpdatedAt()).isEqualTo(now);
        assertThat(pushToken.getLastUsedAt()).isNull();
    }

    @Test
    @DisplayName("Rejects null userId or blank token")
    void rejectsInvalidInputs() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new NotificationPushToken(null, "token", "WEB", now))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new NotificationPushToken(UUID.randomUUID(), "  ", "WEB", now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Supports deactivation, reassignment, and lastUsedAt updates")
    void lifecycleTransitions() {
        UUID firstUser = UUID.randomUUID();
        UUID secondUser = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-11-16T08:00:00Z");
        Instant t2 = Instant.parse("2026-11-16T08:05:00Z");
        Instant t3 = Instant.parse("2026-11-16T08:10:00Z");

        NotificationPushToken pushToken = new NotificationPushToken(firstUser, "fcm-token-123", "WEB", t1);
        pushToken.deactivate(t2);
        assertThat(pushToken.isActive()).isFalse();
        assertThat(pushToken.getUpdatedAt()).isEqualTo(t2);

        pushToken.reassignToUser(secondUser, "WEB", t3);
        assertThat(pushToken.getUserId()).isEqualTo(secondUser);
        assertThat(pushToken.isActive()).isTrue();
        assertThat(pushToken.getUpdatedAt()).isEqualTo(t3);

        pushToken.markUsed(t3);
        assertThat(pushToken.getLastUsedAt()).isEqualTo(t3);
    }
}
