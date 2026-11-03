package com.swiftdrop.delivery.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.delivery.domain.DeliveryProof;

/** The stored proof of delivery, without the photo bytes (fetch those from the photo endpoint). */
record ProofResponse(UUID parcelId, String recipientName, boolean hasPhoto, Integer photoSizeBytes,
        Instant capturedAt) {

    static ProofResponse from(DeliveryProof proof) {
        return new ProofResponse(proof.getParcelId(), proof.getRecipientName(), proof.hasPhoto(),
                proof.getPhotoSizeBytes(), proof.getCapturedAt());
    }

    /** Never includes the recipient name. */
    @Override
    public String toString() {
        return "ProofResponse[parcelId=" + parcelId + ", hasPhoto=" + hasPhoto + "]";
    }
}
