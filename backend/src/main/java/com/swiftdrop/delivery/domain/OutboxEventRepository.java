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
import org.springframework.transaction.annotation.Transactional;

/** Outbox rows; written with status changes, published by the relay (ADR 0004 §3). */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    /** Unpublished rows in outbox order, which is publication order (ADR 0004 §4.1). */
    List<OutboxEvent> findByPublishedAtIsNullOrderByIdAsc(Limit limit);

    /**
     * Takes the relay's transaction-scoped advisory lock (ADR 0004 §4.2); false if another relay holds
     * it. Released when the surrounding transaction ends.
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    boolean tryAdvisoryTransactionLock(@Param("key") long key);

    long countByPublishedAtIsNull();

    Optional<OutboxEvent> findFirstByPublishedAtIsNullOrderByIdAsc();

    long countByPublishedAtIsNullAndAttemptsGreaterThanEqual(int attempts);

    /** Retention: deletes published rows only; unpublished rows are never deleted automatically. */
    @Transactional
    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
