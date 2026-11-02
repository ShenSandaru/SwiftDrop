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
    public static final Set<String> PHOTO_CONTENT_TYPES = Set.of("image/jpeg", "image/png");

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
        Objects.requireNonNull(recipientName, "recipientName");
        if (recipientName.isBlank()) {
            throw new IllegalArgumentException("recipientName must not be blank");
        }
        this.recipientName = recipientName.trim();
        if (photo != null) {
            if (photo.length == 0 || photo.length > MAX_PHOTO_BYTES) {
                throw new IllegalArgumentException("photo must be between 1 byte and 2 MB");
            }
            if (!PHOTO_CONTENT_TYPES.contains(photoContentType)) {
                throw new IllegalArgumentException("photo must be JPEG or PNG");
            }
            this.photo = photo.clone();
            this.photoContentType = photoContentType;
            this.photoSizeBytes = photo.length;
        }
        this.courierId = Objects.requireNonNull(courierId, "courierId");
        this.capturedAt = Objects.requireNonNull(capturedAt, "capturedAt");
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
