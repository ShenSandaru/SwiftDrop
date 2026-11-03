package com.swiftdrop.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;

/** The outbox JSON must match the ADR 0004 §2 envelope exactly; consumers parse these names. */
class ParcelEventSerializerTest {

    private static final UUID EVENT_ID = UUID.fromString("0f4b0000-0000-0000-0000-000000000001");
    private static final UUID PARCEL_ID = UUID.fromString("5a0e0000-0000-0000-0000-000000000001");
    private static final UUID MERCHANT_ID = UUID.fromString("77aa0000-0000-0000-0000-000000000001");

    private final ParcelEventSerializer serializer = new ParcelEventSerializer();

    @Test
    void envelopeAndDataUseTheAdrFieldNamesAndIsoTimes() {
        JsonNode json = JsonMapper.builder().build().readTree(serializer.toJson(failedMerchantEvent()));

        assertThat(json.propertyNames()).containsExactlyInAnyOrder(
                "eventId", "eventType", "schemaVersion", "occurredAt", "producer", "data");
        assertThat(json.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
        assertThat(json.get("eventType").asString()).isEqualTo("parcel.status-changed");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-11-03T12:01:02Z");
        assertThat(json.get("producer").asString()).isEqualTo("delivery");

        JsonNode data = json.get("data");
        assertThat(data.propertyNames()).containsExactlyInAnyOrder("parcelId", "trackingNumber", "previousStatus",
                "newStatus", "sequence", "source", "ownerCustomerId", "ownerMerchantId", "merchantOrderReference",
                "failureReasonCode");
        assertThat(data.get("previousStatus").asString()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(data.get("newStatus").asString()).isEqualTo("FAILED");
        assertThat(data.get("sequence").asInt()).isEqualTo(5);
        assertThat(data.get("source").asString()).isEqualTo("MERCHANT");
        assertThat(data.get("ownerCustomerId").isNull()).isTrue();
        assertThat(data.get("ownerMerchantId").asString()).isEqualTo(MERCHANT_ID.toString());
        assertThat(data.get("merchantOrderReference").asString()).isEqualTo("ORD-10045");
        assertThat(data.get("failureReasonCode").asString()).isEqualTo("RECIPIENT_UNAVAILABLE");
    }

    @Test
    void creationEventHasNullPreviousStatus() {
        ParcelStatusChanged created = ParcelStatusChanged.of(EVENT_ID, Instant.parse("2026-11-03T10:15:30Z"),
                new ParcelStatusChangedData(PARCEL_ID, "SD7K3M9Q2XWB", null, ParcelStatus.CREATED, 1,
                        ParcelSource.CUSTOMER, UUID.randomUUID(), null, null, null));

        JsonNode data = JsonMapper.builder().build().readTree(serializer.toJson(created)).get("data");

        assertThat(data.get("previousStatus").isNull()).isTrue();
        assertThat(data.get("failureReasonCode").isNull()).isTrue();
    }

    @Test
    void jsonReadsBackToTheSameEvent() {
        ParcelStatusChanged event = failedMerchantEvent();

        assertThat(serializer.fromJson(serializer.toJson(event))).isEqualTo(event);
    }

    private static ParcelStatusChanged failedMerchantEvent() {
        return ParcelStatusChanged.of(EVENT_ID, Instant.parse("2026-11-03T12:01:02Z"),
                new ParcelStatusChangedData(PARCEL_ID, "SD7K3M9Q2XWB", ParcelStatus.OUT_FOR_DELIVERY,
                        ParcelStatus.FAILED, 5, ParcelSource.MERCHANT, null, MERCHANT_ID, "ORD-10045",
                        FailureReason.RECIPIENT_UNAVAILABLE));
    }
}
