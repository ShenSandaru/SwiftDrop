package com.swiftdrop.pricing.api;

import java.util.List;

/** Read-only view of the active delivery zones, for modules that must find a zone by name. */
public interface ZoneDirectory {

    /** Active zones ordered by name. */
    List<ServiceZone> findActiveZones();
}
