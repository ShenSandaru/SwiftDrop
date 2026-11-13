package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Merchant API keys (ADR 0008). */
public interface MerchantApiKeyRepository extends JpaRepository<MerchantApiKey, UUID> {

    Optional<MerchantApiKey> findByKeyPrefix(String keyPrefix);

    boolean existsByKeyPrefix(String keyPrefix);

    List<MerchantApiKey> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    Optional<MerchantApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);

    long countByMerchantIdAndRevokedAtIsNull(UUID merchantId);

    /**
     * Sets {@code last_used_at} only if it is unset or older than {@code notUsedSince}, so busy keys
     * cause at most one write per interval (ADR 0008 §3).
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update MerchantApiKey k set k.lastUsedAt = :now
            where k.id = :id and (k.lastUsedAt is null or k.lastUsedAt < :notUsedSince)
            """)
    int recordUse(@Param("id") UUID id, @Param("now") Instant now, @Param("notUsedSince") Instant notUsedSince);
}
