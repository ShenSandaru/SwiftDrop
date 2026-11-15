package com.swiftdrop.delivery.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Direct links between branch hubs (ADR 0011 §1). Pairs are stored smaller ID first, see {@link HubLink}. */
public interface HubLinkRepository extends JpaRepository<HubLink, UUID> {

    Optional<HubLink> findByFirstHubIdAndSecondHubId(UUID firstHubId, UUID secondHubId);

    boolean existsByFirstHubIdAndSecondHubId(UUID firstHubId, UUID secondHubId);

    List<HubLink> findAllByOrderByCreatedAtAsc();

    /** The link between two hubs, given in either order. */
    default Optional<HubLink> findLink(UUID hubId, UUID otherHubId) {
        return findByFirstHubIdAndSecondHubId(HubLink.first(hubId, otherHubId), HubLink.second(hubId, otherHubId));
    }

    /** Whether two different hubs are linked, given in either order. */
    default boolean areLinked(UUID hubId, UUID otherHubId) {
        return !hubId.equals(otherHubId) && existsByFirstHubIdAndSecondHubId(
                HubLink.first(hubId, otherHubId), HubLink.second(hubId, otherHubId));
    }
}
