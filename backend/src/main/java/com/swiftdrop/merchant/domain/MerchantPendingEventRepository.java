package com.swiftdrop.merchant.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Events waiting for an earlier sequence of their parcel (ADR 0004 §4.3). */
public interface MerchantPendingEventRepository extends JpaRepository<MerchantPendingEvent, UUID> {

    Optional<MerchantPendingEvent> findByParcelIdAndSequence(UUID parcelId, int sequence);

    List<MerchantPendingEvent> findByParcelIdOrderBySequenceAsc(UUID parcelId);

    /** Parcels with a pending event due for reconciliation whose state is not stuck, oldest first. */
    @Query("""
            select p.parcelId from MerchantPendingEvent p, MerchantEventState s
            where s.parcelId = p.parcelId and s.stuck = false and p.nextReconcileAt <= :now
            group by p.parcelId order by min(p.nextReconcileAt)
            """)
    List<UUID> findDueParcelIds(@Param("now") Instant now, Limit limit);
}
