package com.swiftdrop.merchant.application;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;

/**
 * Reads and writes {@code parcel.status-changed} envelopes (ADR 0004 §2) for the merchant consumer. Its own
 * mapper with the same settings as delivery's serializer, which merchant may not use (ADR 0003). Unknown
 * fields are ignored, so optional fields added later keep schema version 1.
 */
@Component
public class MerchantEventJson {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * @throws InvalidMerchantEventException if the text is not a complete event of type
     *         {@code parcel.status-changed} and schema version 1
     */
    public ParcelStatusChanged parse(String json) {
        ParcelStatusChanged event;
        try {
            event = mapper.readValue(json, ParcelStatusChanged.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new InvalidMerchantEventException("not a parcel event: " + e.getClass().getSimpleName(), e);
        }
        if (event == null || event.eventId() == null || event.data() == null) {
            throw new InvalidMerchantEventException("event ID or data missing");
        }
        if (!ParcelStatusChanged.EVENT_TYPE.equals(event.eventType())) {
            throw new InvalidMerchantEventException("unsupported event type");
        }
        if (event.schemaVersion() != ParcelStatusChanged.SCHEMA_VERSION) {
            throw new InvalidMerchantEventException("unsupported schema version " + event.schemaVersion());
        }
        ParcelStatusChangedData data = event.data();
        if (data.parcelId() == null || data.newStatus() == null || data.source() == null) {
            throw new InvalidMerchantEventException("parcel ID, status or source missing");
        }
        return event;
    }

    /** The envelope as JSON, for events rebuilt from delivery's history during reconciliation. */
    public String toJson(ParcelStatusChanged event) {
        return mapper.writeValueAsString(event);
    }
}
