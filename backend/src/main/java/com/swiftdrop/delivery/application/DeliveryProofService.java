package com.swiftdrop.delivery.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swiftdrop.delivery.api.ParcelConflictException;
import com.swiftdrop.delivery.api.ParcelNotFoundException;
import com.swiftdrop.delivery.api.ParcelStatus;
import com.swiftdrop.delivery.domain.ActorRole;
import com.swiftdrop.delivery.domain.DeliveryProof;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.Parcel;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.PhotoType;

/**
 * Proof of delivery (COU-3, ADR 0006 §5): captured by the assigned courier while the parcel is out
 * for delivery, then required by the {@code DELIVERED} transition. The photo is private: readable
 * only by the owner, the assigned courier and dispatchers/admins.
 */
@Service
public class DeliveryProofService {

    private final ParcelRepository parcels;
    private final DeliveryProofRepository proofs;
    private final Clock clock;

    @Autowired
    public DeliveryProofService(ParcelRepository parcels, DeliveryProofRepository proofs) {
        this(parcels, proofs, Clock.systemUTC());
    }

    DeliveryProofService(ParcelRepository parcels, DeliveryProofRepository proofs, Clock clock) {
        this.parcels = parcels;
        this.proofs = proofs;
        this.clock = clock;
    }

    /** A stored photo and its detected content type. */
    public record Photo(byte[] bytes, String contentType) {

        /** Never includes the bytes. */
        @Override
        public String toString() {
            return "Photo[contentType=" + contentType + ", size=" + (bytes == null ? 0 : bytes.length) + "]";
        }
    }

    /**
     * Stores or replaces the proof. Locks the parcel so the upload cannot race a status change.
     *
     * @param photo optional; must be a JPEG or PNG image of at most 2 MB, checked by its bytes
     * @throws ParcelNotFoundException if the parcel does not exist or the courier is not assigned to it
     * @throws ParcelConflictException if the parcel is not out for delivery
     * @throws IllegalArgumentException if the name is blank or too long, or the photo is not an allowed image
     */
    @Transactional
    public DeliveryProof capture(UUID parcelId, Actor courier, String recipientName, byte[] photo) {
        if (courier.role() != ActorRole.COURIER) {
            throw new IllegalArgumentException("Only couriers capture proof of delivery");
        }
        Parcel parcel = parcels.findByIdForUpdate(parcelId)
                .filter(found -> courier.userId().equals(found.getAssignedCourierId()))
                .orElseThrow(ParcelNotFoundException::new);
        if (parcel.getStatus() != ParcelStatus.OUT_FOR_DELIVERY) {
            throw new ParcelConflictException("Proof can only be captured while the parcel is out for delivery");
        }
        byte[] image = photo == null || photo.length == 0 ? null : photo;
        String contentType = image == null ? null
                : PhotoType.detect(image).orElseThrow(() -> new IllegalArgumentException("photo must be JPEG or PNG"));
        Instant now = clock.instant();

        Optional<DeliveryProof> existing = proofs.findByParcelId(parcelId);
        if (existing.isPresent()) {
            existing.get().replace(recipientName, image, contentType, courier.userId(), now);
            return existing.get();
        }
        return proofs.save(new DeliveryProof(parcelId, recipientName, image, contentType, courier.userId(), now));
    }

    /**
     * The proof photo for a viewer allowed to see the parcel; empty when there is no photo.
     *
     * @throws ParcelNotFoundException if the parcel does not exist or the viewer may not see it
     */
    @Transactional(readOnly = true)
    public Optional<Photo> photo(UUID parcelId, Viewer viewer) {
        Objects.requireNonNull(viewer, "viewer");
        parcels.findById(parcelId).filter(viewer::canView).orElseThrow(ParcelNotFoundException::new);
        return proofs.findByParcelId(parcelId)
                .filter(DeliveryProof::hasPhoto)
                .map(proof -> new Photo(proof.getPhoto(), proof.getPhotoContentType()));
    }
}
