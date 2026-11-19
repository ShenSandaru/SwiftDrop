package com.swiftdrop.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for managing {@link NotificationPushToken} rows.
 */
public interface NotificationPushTokenRepository extends JpaRepository<NotificationPushToken, UUID> {

    List<NotificationPushToken> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<NotificationPushToken> findByToken(String token);

    @Modifying
    @Query("""
            UPDATE NotificationPushToken t
            SET t.isActive = false,
                t.updatedAt = :now
            WHERE t.token = :token
              AND t.userId = :userId
              AND t.isActive = true
            """)
    int deactivateTokenForUser(
            @Param("token") String token,
            @Param("userId") UUID userId,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            UPDATE NotificationPushToken t
            SET t.lastUsedAt = :now,
                t.updatedAt = :now
            WHERE t.id = :id
            """)
    int updateLastUsedAt(
            @Param("id") UUID id,
            @Param("now") Instant now);
}
