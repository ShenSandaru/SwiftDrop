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

    @Test
    void recipientNameIsLimitedTo200Characters() {
        assertThat(new DeliveryProof(PARCEL_ID, "k".repeat(200), null, null, COURIER_ID, NOW).getRecipientName())
                .hasSize(200);
        assertThatThrownBy(() -> new DeliveryProof(PARCEL_ID, "k".repeat(201), null, null, COURIER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replaceSwapsNameAndPhotoAndCanRemoveThePhoto() {
        DeliveryProof proof = new DeliveryProof(PARCEL_ID, "Kumari", new byte[] {1, 2}, "image/png", COURIER_ID, NOW);
        Instant later = NOW.plusSeconds(30);

        proof.replace("Kumari Silva", new byte[] {7, 8, 9}, "image/jpeg", COURIER_ID, later);
        assertThat(proof.getRecipientName()).isEqualTo("Kumari Silva");
        assertThat(proof.getPhoto()).containsExactly(7, 8, 9);
        assertThat(proof.getPhotoContentType()).isEqualTo("image/jpeg");
        assertThat(proof.getPhotoSizeBytes()).isEqualTo(3);
        assertThat(proof.getCapturedAt()).isEqualTo(later);

        proof.replace("Kumari Silva", null, null, COURIER_ID, later);
        assertThat(proof.hasPhoto()).isFalse();
        assertThat(proof.getPhotoContentType()).isNull();
        assertThat(proof.getPhotoSizeBytes()).isNull();
    }

    @Test
    void failedReplaceLeavesTheProofUnchanged() {
        DeliveryProof proof = new DeliveryProof(PARCEL_ID, "Kumari", new byte[] {1}, "image/png", COURIER_ID, NOW);

        assertThatThrownBy(() -> proof.replace("Other", new byte[] {2}, "image/gif", COURIER_ID, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(proof.getRecipientName()).isEqualTo("Kumari");
        assertThat(proof.getPhoto()).containsExactly(1);
    }
}
