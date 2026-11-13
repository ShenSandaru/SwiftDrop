package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelAssignmentType;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.application.CourierParcelService.CourierParcel;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.pricing.api.ParcelSize;

/**
 * One delivery on the courier's list (COU-2). Contains the contacts and addresses the courier needs.
 *
 * @param task what this courier does with the parcel (ADR 0009 §5)
 * @param taskDone the courier finished this task today
 * @param nextStatuses statuses the courier may set next, in lifecycle order; empty when the task is done
 * @param deliveryAttempts delivery attempts started so far
 */
record CourierParcelResponse(UUID id, String trackingNumber, ParcelStatus status, ParcelAssignmentType task,
        boolean taskDone, List<ParcelStatus> nextStatuses, AddressResponse pickup, AddressResponse dropoff,
        ParcelSize size, int weightGrams, String description, boolean proofCaptured, int deliveryAttempts,
        Instant assignedAt, Instant updatedAt) {

    static CourierParcelResponse from(CourierParcel item) {
        Parcel parcel = item.parcel();
        return new CourierParcelResponse(parcel.getId(), parcel.getTrackingNumber(), parcel.getStatus(), item.task(),
                item.taskDone(), item.nextStatuses().stream().sorted().toList(), AddressResponse.from(parcel.getPickup()),
                AddressResponse.from(parcel.getDropoff()), parcel.getSize(), parcel.getWeightGrams(),
                parcel.getDescription(), item.proofCaptured(), parcel.getDeliveryAttempts(), parcel.getAssignedAt(),
                parcel.getUpdatedAt());
    }

    /** Never includes personal data. */
    @Override
    public String toString() {
        return "CourierParcelResponse[id=" + id + ", trackingNumber=" + trackingNumber + ", status=" + status + "]";
    }
}
