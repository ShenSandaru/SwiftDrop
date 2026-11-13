package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Webhook delivery records (ADR 0004 §7.1). */
public interface MerchantWebhookDeliveryRepository extends JpaRepository<MerchantWebhookDelivery, UUID> {

    /**
     * Creates a {@code PENDING} record due now; does nothing if the event already has a record for this
     * endpoint (the unique constraint is the last guard against a second webhook).
     */
    @Modifying
    @Query(value = """
            INSERT INTO merchant_webhook_deliveries (id, endpoint_id, merchant_id, event_id, parcel_id, sequence,
                event_type, payload, status, attempts, next_attempt_at, manual_redeliveries, created_at)
            VALUES (:id, :endpointId, :merchantId, :eventId, :parcelId, :sequence, :eventType, :payload, 'PENDING', 0,
                :now, 0, :now)
            ON CONFLICT (event_id, endpoint_id) DO NOTHING
            """, nativeQuery = true)
    int insertPending(@Param("id") UUID id, @Param("endpointId") UUID endpointId, @Param("merchantId") UUID merchantId,
            @Param("eventId") UUID eventId, @Param("parcelId") UUID parcelId, @Param("sequence") int sequence,
            @Param("eventType") String eventType, @Param("payload") String payload, @Param("now") Instant now);

    List<MerchantWebhookDelivery> findByEventIdOrderByEndpointId(UUID eventId);

    List<MerchantWebhookDelivery> findByParcelIdOrderBySequenceAsc(UUID parcelId);
}
