-- Owner: delivery module (ADR 0003). Proof of delivery, at most one per parcel (ADR 0006 §5, D-12).
-- Recipient name and photo are personal data, readable only by the owner, the assigned courier and
-- dispatchers/admins.

CREATE TABLE delivery_proofs (
    id                 UUID         PRIMARY KEY,
    parcel_id          UUID         NOT NULL REFERENCES parcels (id),
    recipient_name     VARCHAR(200) NOT NULL,
    -- Optional photo stored in the database for v1 (D-12)
    photo              BYTEA,
    photo_content_type VARCHAR(50),
    photo_size_bytes   INTEGER,
    courier_id         UUID         NOT NULL REFERENCES users (id),
    captured_at        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_delivery_proofs_parcel UNIQUE (parcel_id),
    CONSTRAINT ck_delivery_proofs_recipient_name CHECK (BTRIM(recipient_name) <> ''),
    -- The photo columns are set together or not at all
    CONSTRAINT ck_delivery_proofs_photo_columns CHECK (
        (photo IS NULL) = (photo_content_type IS NULL)
        AND (photo IS NULL) = (photo_size_bytes IS NULL)),
    CONSTRAINT ck_delivery_proofs_photo_type CHECK (photo_content_type IN ('image/jpeg', 'image/png')),
    -- At most 2 MB, and the recorded size matches the stored bytes
    CONSTRAINT ck_delivery_proofs_photo_size CHECK (
        photo_size_bytes BETWEEN 1 AND 2097152 AND photo_size_bytes = OCTET_LENGTH(photo))
);
