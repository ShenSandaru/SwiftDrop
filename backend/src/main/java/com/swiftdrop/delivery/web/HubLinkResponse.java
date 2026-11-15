package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.domain.HubLink;

/** A direct link between two branch hubs (ADR 0011 §1); it has no direction. */
record HubLinkResponse(UUID hubId, UUID otherHubId, Instant createdAt) {

    static HubLinkResponse from(HubLink link) {
        return new HubLinkResponse(link.getFirstHubId(), link.getSecondHubId(), link.getCreatedAt());
    }
}
