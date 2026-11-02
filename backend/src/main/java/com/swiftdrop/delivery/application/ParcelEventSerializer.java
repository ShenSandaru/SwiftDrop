package com.swiftdrop.delivery.application;

import org.springframework.stereotype.Component;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.ParcelStatusChanged;

/**
 * Serializes {@code parcel.status-changed} events for the outbox (ADR 0004 §2). Uses its own mapper
 * so the published format does not change when the application's JSON settings do; times are
 * ISO-8601 strings such as {@code 2026-11-03T10:15:30Z}.
 */
@Component
public class ParcelEventSerializer {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public String toJson(ParcelStatusChanged event) {
        return mapper.writeValueAsString(event);
    }

    public ParcelStatusChanged fromJson(String json) {
        return mapper.readValue(json, ParcelStatusChanged.class);
    }
}
