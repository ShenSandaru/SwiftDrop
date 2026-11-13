package com.swiftdrop.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelSource;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;
import com.swiftdrop.notification.application.NotificationTemplateResolver.NotificationContent;

class NotificationTemplateResolverTest {

    private final NotificationTemplateResolver resolver = new NotificationTemplateResolver();

    private ParcelStatusChanged createEvent(
            ParcelStatus newStatus,
            String trackingNumber,
            Integer attempt,
            FailureReason failureReason) {
        return ParcelStatusChanged.of(
                UUID.randomUUID(),
                Instant.now(),
                new ParcelStatusChangedData(
                        UUID.randomUUID(),
                        trackingNumber,
                        ParcelStatus.CREATED,
                        newStatus,
                        2,
                        ParcelSource.CUSTOMER,
                        UUID.randomUUID(),
                        null,
                        null,
                        failureReason,
                        null,
                        attempt));
    }

    @Test
    @DisplayName("CREATED status resolves title and message with tracking number")
    void createdStatus() {
        ParcelStatusChanged event = createEvent(ParcelStatus.CREATED, "SD1234567890", null, null);
        Optional<NotificationContent> content = resolver.resolve(event);

        assertThat(content).isPresent();
        assertThat(content.get().title()).isEqualTo("Parcel Booked");
        assertThat(content.get().message()).isEqualTo("Your parcel SD1234567890 has been booked.");
    }

    @Test
    @DisplayName("PICKED_UP status resolves title and message with tracking number")
    void pickedUpStatus() {
        ParcelStatusChanged event = createEvent(ParcelStatus.PICKED_UP, "SD9876543210", null, null);
        Optional<NotificationContent> content = resolver.resolve(event);

        assertThat(content).isPresent();
        assertThat(content.get().title()).isEqualTo("Parcel Picked Up");
        assertThat(content.get().message()).isEqualTo("Your parcel SD9876543210 has been collected by our courier.");
    }

    @Test
    @DisplayName("OUT_FOR_DELIVERY status includes attempt number if present")
    void outForDeliveryWithAttempt() {
        ParcelStatusChanged eventWithAttempt = createEvent(ParcelStatus.OUT_FOR_DELIVERY, "SD1112223334", 2, null);
        Optional<NotificationContent> contentWithAttempt = resolver.resolve(eventWithAttempt);

        assertThat(contentWithAttempt).isPresent();
        assertThat(contentWithAttempt.get().title()).isEqualTo("Out for Delivery");
        assertThat(contentWithAttempt.get().message()).isEqualTo("Your parcel SD1112223334 is out for delivery (Attempt 2).");

        ParcelStatusChanged eventWithoutAttempt = createEvent(ParcelStatus.OUT_FOR_DELIVERY, "SD1112223334", null, null);
        Optional<NotificationContent> contentWithoutAttempt = resolver.resolve(eventWithoutAttempt);

        assertThat(contentWithoutAttempt).isPresent();
        assertThat(contentWithoutAttempt.get().message()).isEqualTo("Your parcel SD1112223334 is out for delivery.");
    }

    @Test
    @DisplayName("DELIVERY_ATTEMPT_FAILED status formats attempt and failure reason")
    void deliveryAttemptFailed() {
        ParcelStatusChanged event = createEvent(
                ParcelStatus.DELIVERY_ATTEMPT_FAILED,
                "SD5556667778",
                1,
                FailureReason.RECIPIENT_UNAVAILABLE);
        Optional<NotificationContent> content = resolver.resolve(event);

        assertThat(content).isPresent();
        assertThat(content.get().title()).isEqualTo("Delivery Attempt Failed");
        assertThat(content.get().message())
                .isEqualTo("Delivery attempt 1 for parcel SD5556667778 was unsuccessful. Reason: Recipient unavailable.");
    }

    @Test
    @DisplayName("DELIVERED status resolves title and message")
    void deliveredStatus() {
        ParcelStatusChanged event = createEvent(ParcelStatus.DELIVERED, "SD9998887776", null, null);
        Optional<NotificationContent> content = resolver.resolve(event);

        assertThat(content).isPresent();
        assertThat(content.get().title()).isEqualTo("Parcel Delivered");
        assertThat(content.get().message()).isEqualTo("Your parcel SD9998887776 has been successfully delivered.");
    }

    @Test
    @DisplayName("RETURNED status resolves title and message")
    void returnedStatus() {
        ParcelStatusChanged event = createEvent(ParcelStatus.RETURNED, "SD4443332221", null, null);
        Optional<NotificationContent> content = resolver.resolve(event);

        assertThat(content).isPresent();
        assertThat(content.get().title()).isEqualTo("Parcel Returned");
        assertThat(content.get().message()).isEqualTo("Your parcel SD4443332221 has been returned to the sender.");
    }

    @Test
    @DisplayName("CANCELLED status resolves title and message")
    void cancelledStatus() {
        ParcelStatusChanged event = createEvent(ParcelStatus.CANCELLED, "SD1212121212", null, null);
        Optional<NotificationContent> content = resolver.resolve(event);

        assertThat(content).isPresent();
        assertThat(content.get().title()).isEqualTo("Parcel Cancelled");
        assertThat(content.get().message()).isEqualTo("Your parcel SD1212121212 has been cancelled.");
    }

    @Test
    @DisplayName("FAILED status formats reason code safely")
    void failedStatus() {
        ParcelStatusChanged eventDamaged = createEvent(ParcelStatus.FAILED, "SD3434343434", null, FailureReason.DAMAGED);
        Optional<NotificationContent> contentDamaged = resolver.resolve(eventDamaged);

        assertThat(contentDamaged).isPresent();
        assertThat(contentDamaged.get().title()).isEqualTo("Delivery Failed");
        assertThat(contentDamaged.get().message())
                .isEqualTo("Your parcel SD3434343434 could not be delivered. Reason: Parcel damaged.");

        ParcelStatusChanged eventAddress = createEvent(ParcelStatus.FAILED, "SD3434343434", null, FailureReason.ADDRESS_NOT_FOUND);
        Optional<NotificationContent> contentAddress = resolver.resolve(eventAddress);

        assertThat(contentAddress.get().message())
                .isEqualTo("Your parcel SD3434343434 could not be delivered. Reason: Address not found.");
    }

    @ParameterizedTest
    @EnumSource(value = ParcelStatus.class, names = {"AT_HUB", "IN_TRANSIT", "RETURNING"})
    @DisplayName("Non-notifying statuses return Optional.empty()")
    void nonNotifyingStatuses(ParcelStatus nonNotifyingStatus) {
        ParcelStatusChanged event = createEvent(nonNotifyingStatus, "SD7778889990", null, null);
        Optional<NotificationContent> content = resolver.resolve(event);

        assertThat(content).isEmpty();
    }

    @Test
    @DisplayName("Rejects null event or null event data")
    void rejectsNullEvent() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

