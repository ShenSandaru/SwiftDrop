package com.swiftdrop.pricing.application;

/**
 * No active pricing rule exists for the requested zone pair, or one of the zones is unknown or
 * inactive. Mapped to 409 by the pricing web layer; the message never contains zone IDs.
 */
public class RouteNotPricedException extends RuntimeException {

    RouteNotPricedException() {
        super("No pricing for this route");
    }
}
