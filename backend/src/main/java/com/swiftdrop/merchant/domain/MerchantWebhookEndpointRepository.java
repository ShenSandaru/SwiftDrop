package com.swiftdrop.merchant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * Spring Data JPA repository for merchant webhook subscriptions (ADR 0003).
 */
public interface MerchantWebhookEndpointRepository extends JpaRepository<MerchantWebhookEndpoint, UUID> {

    List<MerchantWebhookEndpoint> findByMerchantIdAndDeletedAtIsNull(UUID merchantId);

    Optional<MerchantWebhookEndpoint> findByIdAndMerchantIdAndDeletedAtIsNull(UUID id, UUID merchantId);

    Optional<MerchantWebhookEndpoint> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            SELECT e FROM MerchantWebhookEndpoint e JOIN e.events ev
            WHERE e.merchantId = :merchantId
              AND e.deletedAt IS NULL
              AND e.enabled = true
              AND ev = :eventType
            """)
    List<MerchantWebhookEndpoint> findActiveForMerchantAndEvent(
            @Param("merchantId") UUID merchantId,
            @Param("eventType") ParcelStatus eventType);
}

