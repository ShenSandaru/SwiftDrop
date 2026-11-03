package com.swiftdrop.delivery.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.PublicTrackingView;

/**
 * Public tracking by tracking number (CUS-6): no login, and only status, cities and timestamps
 * (D-16). Unknown and malformed numbers get the same 404, so numbers cannot be probed. Not rate
 * limited yet (known risk).
 */
@RestController
class TrackingController {

    private final ParcelQueries queries;

    TrackingController(ParcelQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/api/v1/tracked-parcels/{trackingNumber}")
    PublicTrackingView track(@PathVariable String trackingNumber) {
        return queries.findPublicTrackingView(trackingNumber).orElseThrow(ParcelNotFoundException::new);
    }
}
