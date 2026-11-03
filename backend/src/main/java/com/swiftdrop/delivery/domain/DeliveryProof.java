package com.swiftdrop.delivery.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proof of delivery: the recipient's name and an optional photo (ADR 0006 §5, D-12). At most one
 * per parcel. Personal data, readable only by the owner, the assigned courier and dispatchers/admins.
 */
@Entity
@Table(name = "delivery_proofs")
public class DeliveryProof {

    public static final int MAX_PHOTO_BYTES = 2 * 1024 * 1024;
    public static final Set<String> PHOTO_CONTENT_TYPES = Set.of(PhotoType.JPEG, PhotoType.PNG);
    public static final int MAX_RECIPIENT_NAME_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, unique = true, updatable = false)
    private UUID parcelId;

    @Column(name = "recipient_name", nullable = false, length = 200)
    private String recipientName;

    // byte[] maps to PostgreSQL BYTEA; @Lob would map to a large object instead
    @Column(name = "photo")
    private byte[] photo;

    @Column(name = "photo_content_type", length = 50)
    private String photoContentType;

    @Column(name = "photo_size_bytes")
    private Integer photoSizeBytes;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected DeliveryProof() {
        // for JPA
    }

    /**
     * @param photo optional; when given, {@code photoContentType} must be JPEG or PNG. The content
     *        type must already be checked against the bytes by the caller.
     */
    public DeliveryProof(UUID parcelId, String recipientName, byte[] photo, String photoContentType,
            UUID courierId, Instant capturedAt) {
        this.parcelId = Objects.requireNonNull(parcelId, "parcelId");
        apply(recipientName, photo, photoContentType, courierId, capturedAt);
    }

    /**
     * Replaces the proof with a new upload for the same parcel; a missing photo removes the old one.
     * Same rules as the constructor.
     */
    public void replace(String recipientName, byte[] photo, String photoContentType, UUID courierId,
            Instant capturedAt) {
        apply(recipientName, photo, photoContentType, courierId, capturedAt);
    }

    private void apply(String recipientName, byte[] photo, String photoContentType, UUID courierId,
            Instant capturedAt) {
        Objects.requireNonNull(recipientName, "recipientName");
        String trimmed = recipientName.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("recipientName must not be blank");
        }
        if (trimmed.length() > MAX_RECIPIENT_NAME_LENGTH) {
            throw new IllegalArgumentException("recipientName must be at most 200 characters");
        }
        Objects.requireNonNull(courierId, "courierId");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (photo != null) {
            if (photo.length == 0 || photo.length > MAX_PHOTO_BYTES) {
                throw new IllegalArgumentException("photo must be between 1 byte and 2 MB");
            }
            if (!PHOTO_CONTENT_TYPES.contains(photoContentType)) {
                throw new IllegalArgumentException("photo must be JPEG or PNG");
            }
        }
        this.recipientName = trimmed;
        this.photo = photo == null ? null : photo.clone();
        this.photoContentType = photo == null ? null : photoContentType;
        this.photoSizeBytes = photo == null ? null : photo.length;
        this.courierId = courierId;
        this.capturedAt = capturedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public boolean hasPhoto() {
        return photo != null;
    }

    /** A copy of the photo bytes, or {@code null}. */
    public byte[] getPhoto() {
        return photo == null ? null : photo.clone();
    }

    public String getPhotoContentType() {
        return photoContentType;
    }

    public Integer getPhotoSizeBytes() {
        return photoSizeBytes;
    }

    public UUID getCourierId() {
        return courierId;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    /** Never includes the recipient name or photo bytes. */
    @Override
    public String toString() {
        return "DeliveryProof[parcelId=" + parcelId + ", hasPhoto=" + hasPhoto() + ", capturedAt=" + capturedAt + "]";
    }
}
