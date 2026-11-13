package com.swiftdrop.notification.application;

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

class ParcelStatusEventParserTest {

    private final ParcelStatusEventParser parser = new ParcelStatusEventParser();

    @Test
    @DisplayName("Parses a well-formed ParcelStatusChanged JSON payload successfully")
    void parsesValidEvent() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-13T10:15:30Z",
                  "producer": "delivery",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SD7K3M9Q2XWB",
                    "previousStatus": "CREATED",
                    "newStatus": "PICKED_UP",
                    "sequence": 2,
                    "source": "CUSTOMER",
                    "ownerCustomerId": "%s",
                    "ownerMerchantId": null,
                    "merchantOrderReference": null,
                    "failureReasonCode": null,
                    "hubCode": null,
                    "attempt": null
                  }
                }
                """.formatted(eventId, parcelId, customerId);

        ParcelStatusChanged event = parser.parse(json);

        assertThat(event.eventId()).isEqualTo(eventId);
        assertThat(event.eventType()).isEqualTo("parcel.status-changed");
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-13T10:15:30Z"));
        assertThat(event.producer()).isEqualTo("delivery");

        assertThat(event.data().parcelId()).isEqualTo(parcelId);
        assertThat(event.data().trackingNumber()).isEqualTo("SD7K3M9Q2XWB");
        assertThat(event.data().previousStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(event.data().newStatus()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(event.data().sequence()).isEqualTo(2);
        assertThat(event.data().source()).isEqualTo(ParcelSource.CUSTOMER);
        assertThat(event.data().ownerCustomerId()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("Rejects null or blank payload")
    void rejectsNullOrBlank() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(EventParsingException.class)
                .hasMessageContaining("must not be null or blank");

        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(EventParsingException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    @DisplayName("Rejects malformed JSON syntax")
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> parser.parse("{not-valid-json}"))
                .isInstanceOf(EventParsingException.class)
                .hasMessageContaining("Failed to deserialize event payload");
    }

    @Test
    @DisplayName("Rejects invalid UUID format")
    void rejectsInvalidUuid() {
        String json = """
                {
                  "eventId": "not-a-valid-uuid",
                  "eventType": "parcel.status-changed",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-13T10:15:30Z",
                  "producer": "delivery",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SD7K3M9Q2XWB",
                    "newStatus": "CREATED",
                    "sequence": 1,
                    "source": "CUSTOMER"
                  }
                }
                """.formatted(UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(EventParsingException.class)
                .hasMessageContaining("Failed to deserialize event payload");
    }

    @Test
    @DisplayName("Rejects unsupported eventType")
    void rejectsUnsupportedEventType() {
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "unsupported.event.type",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-13T10:15:30Z",
                  "producer": "delivery",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SD7K3M9Q2XWB",
                    "newStatus": "CREATED",
                    "sequence": 1,
                    "source": "CUSTOMER"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(EventParsingException.class)
                .hasMessageContaining("Unsupported eventType: unsupported.event.type");
    }

    @Test
    @DisplayName("Rejects unsupported schemaVersion")
    void rejectsUnsupportedSchemaVersion() {
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "schemaVersion": 99,
                  "occurredAt": "2026-11-13T10:15:30Z",
                  "producer": "delivery",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SD7K3M9Q2XWB",
                    "newStatus": "CREATED",
                    "sequence": 1,
                    "source": "CUSTOMER"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(EventParsingException.class)
                .hasMessageContaining("Unsupported schemaVersion: 99");
    }

    @Test
    @DisplayName("Rejects missing required data fields such as trackingNumber or newStatus")
    void rejectsMissingDataFields() {
        String jsonWithoutTracking = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-13T10:15:30Z",
                  "producer": "delivery",
                  "data": {
                    "parcelId": "%s",
                    "newStatus": "CREATED",
                    "sequence": 1,
                    "source": "CUSTOMER"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(jsonWithoutTracking))
                .isInstanceOf(EventParsingException.class);
    }

    @Test
    @DisplayName("Rejects invalid newStatus enum value")
    void rejectsInvalidNewStatusEnumValue() {
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-13T10:15:30Z",
                  "producer": "delivery",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SD7K3M9Q2XWB",
                    "newStatus": "NON_EXISTENT_STATUS",
                    "sequence": 1,
                    "source": "CUSTOMER"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(EventParsingException.class)
                .hasMessage("Failed to deserialize event payload");
    }

    @Test
    @DisplayName("Rejects null required envelope field or data field")
    void rejectsNullRequiredEnvelopeOrDataField() {
        // Null envelope occurredAt
        String jsonNullOccurredAt = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "schemaVersion": 1,
                  "occurredAt": null,
                  "producer": "delivery",
                  "data": {
                    "parcelId": "%s",
                    "trackingNumber": "SD7K3M9Q2XWB",
                    "newStatus": "CREATED",
                    "sequence": 1,
                    "source": "CUSTOMER"
                  }
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(jsonNullOccurredAt))
                .isInstanceOf(EventParsingException.class);

        // Null data parcelId
        String jsonNullParcelId = """
                {
                  "eventId": "%s",
                  "eventType": "parcel.status-changed",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-13T10:15:30Z",
                  "producer": "delivery",
                  "data": {
                    "parcelId": null,
                    "trackingNumber": "SD7K3M9Q2XWB",
                    "newStatus": "CREATED",
                    "sequence": 1,
                    "source": "CUSTOMER"
                  }
                }
                """.formatted(UUID.randomUUID());

        assertThatThrownBy(() -> parser.parse(jsonNullParcelId))
                .isInstanceOf(EventParsingException.class);
    }
}

