package com.swiftdrop.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DeliveryProofTest {

    private static final UUID PARCEL_ID = UUID.randomUUID();
    private static final UUID COURIER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30Z");

    @Test
    void proofWithoutPhotoNeedsOnlyRecipientName() {
        DeliveryProof proof = new DeliveryProof(PARCEL_ID, "  Kumari Silva ", null, null, COURIER_ID, NOW);

        assertThat(proof.getRecipientName()).isEqualTo("Kumari Silva");
        assertThat(proof.hasPhoto()).isFalse();
        assertThat(proof.getPhotoSizeBytes()).isNull();
    }

    @Test
    void photoIsCopiedAndItsSizeRecorded() {
        byte[] photo = {1, 2, 3};
        DeliveryProof proof = new DeliveryProof(PARCEL_ID, "Kumari Silva", photo, "image/png", COURIER_ID, NOW);
        photo[0] = 9;

        assertThat(proof.getPhoto()).containsExactly(1, 2, 3);
        assertThat(proof.getPhotoSizeBytes()).isEqualTo(3);
        proof.getPhoto()[1] = 9;
        assertThat(proof.getPhoto()).containsExactly(1, 2, 3);
    }

    @Test
    void blankRecipientNameIsRejected() {
        assertThatThrownBy(() -> new DeliveryProof(PARCEL_ID, " ", null, null, COURIER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void photoMustBeJpegOrPngAndAtMostTwoMegabytes() {
        assertThatThrownBy(() -> new DeliveryProof(PARCEL_ID, "Kumari", new byte[] {1}, "image/gif", COURIER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryProof(PARCEL_ID, "Kumari", new byte[0], "image/png", COURIER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeliveryProof(PARCEL_ID, "Kumari",
                new byte[DeliveryProof.MAX_PHOTO_BYTES + 1], "image/jpeg", COURIER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DeliveryProof(PARCEL_ID, "Kumari", new byte[DeliveryProof.MAX_PHOTO_BYTES], "image/jpeg",
                COURIER_ID, NOW).getPhotoSizeBytes()).isEqualTo(DeliveryProof.MAX_PHOTO_BYTES);
    }

    @Test
    void toStringNeverContainsRecipientName() {
        assertThat(new DeliveryProof(PARCEL_ID, "Kumari Silva", null, null, COURIER_ID, NOW).toString())
                .doesNotContain("Kumari");
    }
}
