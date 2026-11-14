package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Applied event IDs (ADR 0004 §4.3). */
public interface MerchantProcessedEventRepository extends JpaRepository<MerchantProcessedEvent, UUID> {

    Optional<MerchantProcessedEvent> findByParcelIdAndSequence(UUID parcelId, int sequence);

    /** Deletes at most {@code limit} IDs older than the cutoff, so retention runs in short transactions. */
    @Modifying
    @Query(value = """
            DELETE FROM merchant_processed_events WHERE event_id IN (
                SELECT event_id FROM merchant_processed_events WHERE processed_at < :cutoff LIMIT :limit)
            """, nativeQuery = true)
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}
