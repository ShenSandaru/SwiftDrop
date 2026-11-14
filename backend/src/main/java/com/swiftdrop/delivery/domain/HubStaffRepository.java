package com.swiftdrop.delivery.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Home hubs of dispatchers and couriers (ADR 0012 §1). */
public interface HubStaffRepository extends JpaRepository<HubStaff, UUID> {

    /** Every row of a user: their hubs and, if present, the head office row. */
    List<HubStaff> findByUserId(UUID userId);

    /** The staff of the given hubs; never head office rows. */
    List<HubStaff> findByHubIdIn(Collection<UUID> hubIds);

    Optional<HubStaff> findByUserIdAndHubId(UUID userId, UUID hubId);

    Optional<HubStaff> findByUserIdAndHubIdIsNull(UUID userId);

    List<HubStaff> findAllByOrderByCreatedAtAsc();
}
