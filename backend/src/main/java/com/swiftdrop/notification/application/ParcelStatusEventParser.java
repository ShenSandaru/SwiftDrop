package com.swiftdrop.notification.application;

import org.springframework.stereotype.Component;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * Deserializes and validates {@code parcel.status-changed} domain event envelopes
 * for the notification module (ADR 0004 §2, §5).
 */
@Component
public class ParcelStatusEventParser {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    /**
     * Parses and validates a raw JSON string into a {@link ParcelStatusChanged} envelope.
     *
     * @param json the raw message payload
     * @return the strongly-typed, validated event envelope
     * @throws EventParsingException if the payload is null, malformed, or violates schema validation
     */
    public ParcelStatusChanged parse(String json) {
        if (json == null || json.isBlank()) {
            throw new EventParsingException("Event payload must not be null or blank");
        }

        ParcelStatusChanged event;
        try {
            event = mapper.readValue(json, ParcelStatusChanged.class);
        } catch (Exception e) {
            throw new EventParsingException("Failed to deserialize event payload", e);
        }

        validate(event);
        return event;
    }

    private void validate(ParcelStatusChanged event) {
        if (event.eventId() == null) {
            throw new EventParsingException("Envelope eventId must not be null");
        }
        if (!ParcelStatusChanged.EVENT_TYPE.equals(event.eventType())) {
            throw new EventParsingException("Unsupported eventType: " + event.eventType()
                    + " (expected: " + ParcelStatusChanged.EVENT_TYPE + ")");
        }
        if (event.schemaVersion() != ParcelStatusChanged.SCHEMA_VERSION) {
            throw new EventParsingException("Unsupported schemaVersion: " + event.schemaVersion()
                    + " (expected: " + ParcelStatusChanged.SCHEMA_VERSION + ")");
        }
        if (event.occurredAt() == null) {
            throw new EventParsingException("Envelope occurredAt must not be null");
        }
        if (event.producer() == null || event.producer().isBlank()) {
            throw new EventParsingException("Envelope producer must not be null or blank");
        }
        if (event.data() == null) {
            throw new EventParsingException("Envelope data payload must not be null");
        }

        // Validate core data fields
        if (event.data().parcelId() == null) {
            throw new EventParsingException("Parcel data parcelId must not be null");
        }
        if (event.data().trackingNumber() == null || event.data().trackingNumber().isBlank()) {
            throw new EventParsingException("Parcel data trackingNumber must not be null or blank");
        }
        if (event.data().newStatus() == null) {
            throw new EventParsingException("Parcel data newStatus must not be null");
        }
        if (event.data().source() == null) {
            throw new EventParsingException("Parcel data source must not be null");
        }
        if (event.data().sequence() < 1) {
            throw new EventParsingException("Parcel data sequence must be >= 1, but was: " + event.data().sequence());
        }
    }
}

