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

    /**
     * IDs of records due for sending: {@code PENDING} and due, or {@code IN_PROGRESS} with an expired lease (the
     * sender that held it crashed or was too slow). Locks them for the claiming transaction; records locked by
     * another sender are skipped.
     */
    @Query(value = """
            SELECT id FROM merchant_webhook_deliveries
            WHERE (status = 'PENDING' AND next_attempt_at <= :now)
               OR (status = 'IN_PROGRESS' AND lease_expires_at <= :now)
            ORDER BY next_attempt_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<UUID> lockDueIds(@Param("now") Instant now, @Param("limit") int limit);

    /** Claims locked records for one sender with a fresh lease token. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE merchant_webhook_deliveries
            SET status = 'IN_PROGRESS', lease_token = :token, lease_expires_at = :expiresAt
            WHERE id IN (:ids)
            """, nativeQuery = true)
    int claim(@Param("ids") List<UUID> ids, @Param("token") UUID token, @Param("expiresAt") Instant expiresAt);

    /**
     * Stores the outcome of an attempt and releases the lease, but only for the sender that still holds a valid
     * lease: the record must be {@code IN_PROGRESS}, carry this token, and the lease must not have expired.
     *
     * @return 1 if stored, 0 if the lease was lost (expired or taken over)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE merchant_webhook_deliveries
            SET status = :status, attempts = attempts + :attemptIncrement, next_attempt_at = :nextAttemptAt,
                completed_at = CAST(:completedAt AS TIMESTAMPTZ), last_outcome = :lastOutcome,
                last_http_status = CAST(:lastHttpStatus AS INTEGER),
                lease_token = NULL, lease_expires_at = NULL
            WHERE id = :id AND status = 'IN_PROGRESS' AND lease_token = :token AND lease_expires_at > :now
            """, nativeQuery = true)
    int finishAttempt(@Param("id") UUID id, @Param("token") UUID token, @Param("now") Instant now,
            @Param("status") String status, @Param("attemptIncrement") int attemptIncrement,
            @Param("nextAttemptAt") Instant nextAttemptAt, @Param("completedAt") Instant completedAt,
            @Param("lastOutcome") String lastOutcome, @Param("lastHttpStatus") Integer lastHttpStatus);

    List<MerchantWebhookDelivery> findByEventIdOrderByEndpointId(UUID eventId);

    List<MerchantWebhookDelivery> findByParcelIdOrderBySequenceAsc(UUID parcelId);
}
