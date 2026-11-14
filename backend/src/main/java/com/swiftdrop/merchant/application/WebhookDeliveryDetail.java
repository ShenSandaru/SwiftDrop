package com.swiftdrop.merchant.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.swiftdrop.delivery.api.ParcelStatus;

/**
 * One delivery record with its attempts grouped by series, for delivery management (SD-412). Holds only what is
 * needed to manage deliveries: never the payload, request headers, signatures or secrets.
 *
 * @param redeliveriesUsed      manual redeliveries started so far, 0 to 3
 * @param redeliveriesRemaining manual redeliveries still allowed
 * @param redeliverable         whether a manual redelivery would be accepted now (status and cap; the endpoint
 *                              and merchant are checked when it is requested)
 * @param series                series 0 (automatic) first, then each manual redelivery
 */
public record WebhookDeliveryDetail(
        UUID id,
        UUID merchantId,
        UUID endpointId,
        String endpointUrl,
        UUID eventId,
        UUID parcelId,
        int sequence,
        ParcelStatus eventType,
        String status,
        int attemptCount,
        Integer lastHttpStatus,
        String lastOutcome,
        Instant nextAttemptAt,
        Instant createdAt,
        Instant completedAt,
        int redeliveriesUsed,
        int redeliveriesRemaining,
        boolean redeliverable,
        List<Series> series) {

    /**
     * One attempt series.
     *
     * @param number          0 for the automatic series, 1 to 3 for manual redeliveries
     * @param requestedBy     the user who requested the redelivery, null for series 0
     * @param requestedByRole {@code MERCHANT} or {@code ADMIN}, null for series 0
     * @param requestedAt     when the redelivery was requested, null for series 0
     * @param previousStatus  the record's status when the redelivery was requested, null for series 0
     */
    public record Series(int number, UUID requestedBy, String requestedByRole, Instant requestedAt,
            String previousStatus, List<Attempt> attempts) {
    }

    /** One attempt: outcome category only, no response body. */
    public record Attempt(int number, Instant attemptedAt, String outcome, Integer httpStatus, int durationMs) {
    }
}
