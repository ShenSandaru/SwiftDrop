package com.swiftdrop.notification.application;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.swiftdrop.delivery.api.FailureReason;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.api.ParcelStatusChanged;
import com.swiftdrop.delivery.api.ParcelStatusChangedData;

/**
 * Resolves user-facing notification titles and message templates for parcel status changes
 * based on the approved notification matrix (docs/reviews/notification-phase-5.2-assessment.md).
 */
@Component
public class NotificationTemplateResolver {

    /**
     * Immutable rendered notification content containing the resolved title and message.
     */
    public record NotificationContent(String title, String message) {
        public NotificationContent {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("title must not be null or blank");
            }
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message must not be null or blank");
            }
        }
    }

    /**
     * Resolves the notification title and message for a parcel status event.
     *
     * @param event the parcel status changed event
     * @return an {@link Optional} containing {@link NotificationContent} if the status generates
     *         a notification, or {@link Optional#empty()} if the status is non-notifying
     * @throws IllegalArgumentException if event or its data is null
     */
    public Optional<NotificationContent> resolve(ParcelStatusChanged event) {
        if (event == null || event.data() == null) {
            throw new IllegalArgumentException("event and event.data must not be null");
        }

        ParcelStatusChangedData data = event.data();
        ParcelStatus status = data.newStatus();
        String trackingNumber = data.trackingNumber();

        return switch (status) {
            case CREATED -> Optional.of(new NotificationContent(
                    "Parcel Booked",
                    "Your parcel " + trackingNumber + " has been booked."));

            case PICKED_UP -> Optional.of(new NotificationContent(
                    "Parcel Picked Up",
                    "Your parcel " + trackingNumber + " has been collected by our courier."));

            case OUT_FOR_DELIVERY -> {
                String attemptSuffix = data.attempt() != null ? " (Attempt " + data.attempt() + ")" : "";
                yield Optional.of(new NotificationContent(
                        "Out for Delivery",
                        "Your parcel " + trackingNumber + " is out for delivery" + attemptSuffix + "."));
            }

            case DELIVERY_ATTEMPT_FAILED -> {
                String attemptPrefix = data.attempt() != null ? "attempt " + data.attempt() + " for " : "";
                String reasonDesc = formatFailureReason(data.failureReasonCode());
                yield Optional.of(new NotificationContent(
                        "Delivery Attempt Failed",
                        "Delivery " + attemptPrefix + "parcel " + trackingNumber + " was unsuccessful. Reason: " + reasonDesc + "."));
            }

            case DELIVERED -> Optional.of(new NotificationContent(
                    "Parcel Delivered",
                    "Your parcel " + trackingNumber + " has been successfully delivered."));

            case RETURNED -> Optional.of(new NotificationContent(
                    "Parcel Returned",
                    "Your parcel " + trackingNumber + " has been returned to the sender."));

            case CANCELLED -> Optional.of(new NotificationContent(
                    "Parcel Cancelled",
                    "Your parcel " + trackingNumber + " has been cancelled."));

            case FAILED -> {
                String reasonDesc = formatFailureReason(data.failureReasonCode());
                yield Optional.of(new NotificationContent(
                        "Delivery Failed",
                        "Your parcel " + trackingNumber + " could not be delivered. Reason: " + reasonDesc + "."));
            }

            case AT_HUB, IN_TRANSIT, RETURNING -> Optional.empty();
        };
    }

    /**
     * Resolves operational notification title and message for enabled dispatchers.
     * Only operational exception and dispatch-queue statuses generate dispatcher alerts.
     *
     * @param event the parcel status changed event
     * @return an {@link Optional} containing {@link NotificationContent} for supported
     *         dispatcher statuses, or {@link Optional#empty()} otherwise
     */
    public Optional<NotificationContent> resolveForDispatcher(ParcelStatusChanged event) {
        if (event == null || event.data() == null) {
            throw new IllegalArgumentException("event and event.data must not be null");
        }

        ParcelStatusChangedData data = event.data();
        ParcelStatus status = data.newStatus();
        String trackingNumber = data.trackingNumber();

        return switch (status) {
            case CREATED -> Optional.of(new NotificationContent(
                    "New Parcel Awaiting Dispatch",
                    "Parcel " + trackingNumber + " has been created and is awaiting dispatch."));

            case DELIVERY_ATTEMPT_FAILED -> {
                String attemptSuffix = data.attempt() != null ? " (Attempt " + data.attempt() + ")" : "";
                String reasonDesc = formatFailureReason(data.failureReasonCode());
                yield Optional.of(new NotificationContent(
                        "Delivery Attempt Failed",
                        "Delivery attempt failed for parcel " + trackingNumber + attemptSuffix + ". Reason: " + reasonDesc + "."));
            }

            case FAILED -> {
                String reasonDesc = formatFailureReason(data.failureReasonCode());
                yield Optional.of(new NotificationContent(
                        "Parcel Delivery Failed",
                        "Parcel " + trackingNumber + " has permanently failed delivery. Reason: " + reasonDesc + "."));
            }

            case RETURNED -> Optional.of(new NotificationContent(
                    "Parcel Returned",
                    "Parcel " + trackingNumber + " has been returned to the sender."));

            case PICKED_UP, AT_HUB, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, RETURNING, CANCELLED -> Optional.empty();
        };
    }

    private static String formatFailureReason(FailureReason reason) {
        if (reason == null) {
            return "Unspecified reason";
        }
        return switch (reason) {
            case RECIPIENT_UNAVAILABLE -> "Recipient unavailable";
            case ADDRESS_NOT_FOUND -> "Address not found";
            case REFUSED -> "Delivery refused by recipient";
            case DAMAGED -> "Parcel damaged";
            case OTHER -> "Other issue";
        };
    }
}

