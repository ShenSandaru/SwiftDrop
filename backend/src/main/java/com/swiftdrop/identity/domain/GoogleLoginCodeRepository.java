package com.swiftdrop.identity.domain;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoogleLoginCodeRepository extends JpaRepository<GoogleLoginCode, String> {

    /**
     * Atomically marks the exchange code as used if it exists, is not yet used, and has not expired.
     * Returns 1 if successfully marked used, 0 otherwise (invalid, expired, or already used).
     */
    @Modifying
    @Query("UPDATE GoogleLoginCode c SET c.usedAt = :now WHERE c.codeHash = :codeHash AND c.usedAt IS NULL AND c.expiresAt > :now")
    int markUsedIfValid(@Param("codeHash") String codeHash, @Param("now") Instant now);

    /**
     * Bounded opportunistic cleanup of expired and sufficiently old redeemed codes.
     */
    @Modifying
    @Query(value = """
        DELETE FROM google_login_codes
        WHERE code_hash IN (
            SELECT code_hash FROM google_login_codes
            WHERE expires_at < :expiredBefore
               OR (used_at IS NOT NULL AND used_at < :usedBefore)
            LIMIT :limit
        )
        """, nativeQuery = true)
    int deleteExpiredOrUsedBefore(
            @Param("expiredBefore") Instant expiredBefore,
            @Param("usedBefore") Instant usedBefore,
            @Param("limit") int limit);
}
