package com.swiftdrop.tracking.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.swiftdrop.tracking.application.LiveTracking;

/**
 * Public live tracking stream (ADR 0007, SYS-1, CUS-6). No login; carries only what the public
 * tracking view shows. Covered by the public {@code GET /api/v1/tracked-parcels/**} security rule.
 */
@RestController
class TrackingStreamController {

    private final LiveTracking liveTracking;

    TrackingStreamController(LiveTracking liveTracking) {
        this.liveTracking = liveTracking;
    }

    @GetMapping(path = "/api/v1/tracked-parcels/{trackingNumber}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable String trackingNumber) {
        return liveTracking.subscribe(trackingNumber);
    }
}
