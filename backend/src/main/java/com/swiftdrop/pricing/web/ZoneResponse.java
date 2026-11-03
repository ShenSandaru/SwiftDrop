package com.swiftdrop.pricing.web;

import java.util.UUID;

import com.swiftdrop.pricing.domain.Zone;

/** A delivery zone as offered in the booking form. */
record ZoneResponse(UUID id, String code, String name) {

    static ZoneResponse from(Zone zone) {
        return new ZoneResponse(zone.getId(), zone.getCode(), zone.getName());
    }
}
