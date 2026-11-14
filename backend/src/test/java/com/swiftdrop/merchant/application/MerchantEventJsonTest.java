package com.swiftdrop.merchant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;

class MerchantEventJsonTest {

    private final MerchantEventJson json = new MerchantEventJson();

    @Test
    @DisplayName("Events round-trip, including the ADR 0009 hubCode and attempt fields")
    void roundTrip() {
        ParcelStatusChanged event = ParcelStatusChanged.of(UUID.randomUUID(), Instant.parse("2026-11-14T08:00:00Z"),
                new ParcelStatusChangedData(UUID.randomUUID(), "SD7K3M9Q2XWB", ParcelStatus.OUT_FOR_DELIVERY,
                        ParcelStatus.DELIVERY_ATTEMPT_FAILED, 7, ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-1",
                        FailureReason.RECIPIENT_UNAVAILABLE, null, 2));

        String text = json.toJson(event);

        assertThat(json.parse(text)).isEqualTo(event);
        assertThat(text).contains("\"occurredAt\":\"2026-11-14T08:00:00Z\"");
    }

    @Test
    @DisplayName("Unknown fields are ignored, so new optional fields keep schema version 1")
    void unknownFieldsIgnored() {
        ParcelStatusChanged event = ParcelStatusChanged.of(UUID.randomUUID(), Instant.parse("2026-11-14T08:00:00Z"),
                new ParcelStatusChangedData(UUID.randomUUID(), "SD7K3M9Q2XWB", null, ParcelStatus.CREATED, 1,
                        ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-1", null));
        String text = json.toJson(event).replace("\"producer\"", "\"futureField\":true,\"producer\"");

        assertThat(json.parse(text)).isEqualTo(event);
    }

    @Test
    @DisplayName("Missing data, a wrong type or schema version, and broken JSON are invalid")
    void invalid() {
        String valid = json.toJson(ParcelStatusChanged.of(UUID.randomUUID(), Instant.now(),
                new ParcelStatusChangedData(UUID.randomUUID(), "SD7K3M9Q2XWB", null, ParcelStatus.CREATED, 1,
                        ParcelSource.MERCHANT, null, UUID.randomUUID(), "ORD-1", null)));

        for (String text : new String[] {"", "[]", "{\"eventId\":\"" + UUID.randomUUID() + "\"}",
                valid.replace("parcel.status-changed", "parcel.created"),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
                valid.replace("\"sequence\":1", "\"sequence\":0")}) {
            assertThatThrownBy(() -> json.parse(text)).as(text).isInstanceOf(InvalidMerchantEventException.class);
        }
    }
}
