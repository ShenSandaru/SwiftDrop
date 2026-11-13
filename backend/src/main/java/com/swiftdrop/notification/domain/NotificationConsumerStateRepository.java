package com.swiftdrop.notification.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for managing {@link NotificationConsumerState} per-parcel sequence watermarks
 * and acquiring per-parcel transaction-level advisory locks.
 */
public interface NotificationConsumerStateRepository extends JpaRepository<NotificationConsumerState, UUID> {

    /**
     * Acquires an exclusive transaction-level advisory lock on the parcel UUID (ADR 0004 §4.3).
     * Uses PostgreSQL's 64-bit {@code hashtextextended} function with a zero seed.
     * The lock is held until the surrounding database transaction commits or rolls back.
     *
     * @param parcelId the parcel identifier to lock
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:parcelId AS text), 0))", nativeQuery = true)
    void acquireParcelLock(@Param("parcelId") UUID parcelId);

    /**
     * Attempts to acquire an exclusive transaction-level advisory lock on the parcel UUID without blocking.
     * Returns {@code true} if acquired, {@code false} if another transaction currently holds the lock.
     *
     * @param parcelId the parcel identifier to lock
     * @return whether the lock was successfully acquired
     */
    @Query(value = "SELECT pg_try_advisory_xact_lock(hashtextextended(CAST(:parcelId AS text), 0))", nativeQuery = true)
    boolean tryAcquireParcelLock(@Param("parcelId") UUID parcelId);
}


