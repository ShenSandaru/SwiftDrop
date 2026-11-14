package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Per-parcel consumer state (ADR 0004 §4.3). */
public interface MerchantEventStateRepository extends JpaRepository<MerchantEventState, UUID> {

    /** Creates the state row unless it exists; concurrent first events of a parcel both succeed. */
    @Modifying
    @Query(value = """
            INSERT INTO merchant_event_states (parcel_id, merchant_id, last_applied_sequence, stuck, updated_at)
            VALUES (:parcelId, :merchantId, 0, FALSE, :now)
            ON CONFLICT (parcel_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("parcelId") UUID parcelId, @Param("merchantId") UUID merchantId,
            @Param("now") Instant now);

    /** Locks the row until the transaction ends; waits for another holder. */
    @Query(value = "SELECT * FROM merchant_event_states WHERE parcel_id = :parcelId FOR UPDATE", nativeQuery = true)
    Optional<MerchantEventState> lockByParcelId(@Param("parcelId") UUID parcelId);

    /** Locks the row unless another transaction holds it (empty then); used by the reconciler. */
    @Query(value = "SELECT * FROM merchant_event_states WHERE parcel_id = :parcelId FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<MerchantEventState> tryLockByParcelId(@Param("parcelId") UUID parcelId);

    long countByStuckTrue();
}
