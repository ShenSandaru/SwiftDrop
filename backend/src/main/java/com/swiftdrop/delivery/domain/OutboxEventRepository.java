package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Outbox rows; written with status changes, published by the relay (ADR 0004 §3). */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    /**
     * Takes the relay's transaction-scoped advisory lock without waiting (ADR 0004 §4.2). Returns
     * {@code false} if another relay holds it. The lock is released when the transaction ends.
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryRelayLock(@Param("key") long key);

    /** Unpublished rows in publication (outbox ID) order, due or not. */
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    long countByPublishedAtIsNull();

    Optional<OutboxEvent> findFirstByPublishedAtIsNullOrderByIdAsc();

    long countByPublishedAtIsNullAndAttemptsGreaterThanEqual(int attempts);

    List<OutboxEvent> findByPublishedAtIsNullAndAttemptsGreaterThanEqualOrderByIdAsc(int attempts, Limit limit);

    /** Deletes published rows older than the cutoff; unpublished rows are never deleted. */
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.publishedAt IS NOT NULL AND o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
