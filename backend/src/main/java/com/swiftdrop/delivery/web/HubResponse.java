package com.swiftdrop.delivery.web;

import java.util.UUID;

import com.swiftdrop.delivery.domain.Hub;

/** A hub of the network (ADR 0009 §1). */
record HubResponse(UUID id, String code, String name, String city, UUID zoneId, boolean central,
        boolean active) {

    static HubResponse from(Hub hub) {
        return new HubResponse(hub.getId(), hub.getCode(), hub.getName(), hub.getCity(), hub.getZoneId(),
                hub.isCentral(), hub.isActive());
    }
}
