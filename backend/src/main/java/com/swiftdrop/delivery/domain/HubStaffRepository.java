package com.swiftdrop.delivery.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Home hubs of dispatchers and couriers (ADR 0012 §1). */
public interface HubStaffRepository extends JpaRepository<HubStaff, UUID> {

    /** Every hub a user works for. */
    List<HubStaff> findByUserId(UUID userId);

    /** The staff of the given hubs. */
    List<HubStaff> findByHubIdIn(Collection<UUID> hubIds);

    Optional<HubStaff> findByUserIdAndHubId(UUID userId, UUID hubId);

    List<HubStaff> findAllByOrderByCreatedAtAsc();
}
