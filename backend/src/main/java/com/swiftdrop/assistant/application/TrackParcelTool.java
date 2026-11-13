package com.swiftdrop.assistant.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.PublicStatusEvent;
import com.swiftdrop.delivery.api.PublicTrackingView;

/** Where a parcel is, from its tracking number: the same public data as the tracking page. */
@Component
class TrackParcelTool implements AssistantTool {

    static final String NAME = "track_parcel";
    private static final int MAX_TRACKING_NUMBER_LENGTH = 40;

    private final ParcelQueries parcels;

    TrackParcelTool(ParcelQueries parcels) {
        this.parcels = parcels;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME,
                "Find the current status and journey of a parcel from its tracking number.",
                Map.of("type", "object",
                        "properties", Map.of("trackingNumber", Map.of("type", "string",
                                "description", "The parcel's tracking number, for example SD1234567890")),
                        "required", List.of("trackingNumber")));
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> arguments) {
        Object raw = arguments.get("trackingNumber");
        if (!(raw instanceof String text) || text.isBlank()) {
            return Map.of("error", "A tracking number is required.");
        }
        String trackingNumber = text.trim();
        if (trackingNumber.length() > MAX_TRACKING_NUMBER_LENGTH) {
            return Map.of("error", "That does not look like a tracking number.");
        }
        Optional<PublicTrackingView> view = parcels.findPublicTrackingView(trackingNumber);
        if (view.isEmpty()) {
            return Map.of("found", false, "message", "No parcel was found for that tracking number.");
        }
        return describe(view.get());
    }

    private static Map<String, Object> describe(PublicTrackingView view) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("found", true);
        result.put("trackingNumber", view.trackingNumber());
        result.put("status", view.status().name());
        result.put("from", view.pickupCity());
        result.put("to", view.dropoffCity());
        result.put("history", view.history().stream().map(TrackParcelTool::describe).toList());
        return result;
    }

    private static Map<String, Object> describe(PublicStatusEvent event) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("status", event.status().name());
        entry.put("at", event.occurredAt().toString());
        if (event.hubName() != null) {
            entry.put("hub", event.hubName());
        }
        if (event.attempt() != null) {
            entry.put("attempt", event.attempt());
        }
        return entry;
    }
}
