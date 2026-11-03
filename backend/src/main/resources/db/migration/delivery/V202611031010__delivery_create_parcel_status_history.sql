-- Owner: delivery module (ADR 0003). One row per parcel status change, never updated or deleted.
-- Outbox events can be rebuilt from these rows with their original IDs (ADR 0004 §4.3).

CREATE TABLE parcel_status_history (
    id                  UUID         PRIMARY KEY,
    parcel_id           UUID         NOT NULL REFERENCES parcels (id),
    sequence            INTEGER      NOT NULL,
    from_status         VARCHAR(30),
    to_status           VARCHAR(30)  NOT NULL,
    -- Null when the actor is not a user, for example the merchant API
    actor_user_id       UUID         REFERENCES users (id),
    actor_role          VARCHAR(50)  NOT NULL,
    -- Free text from the courier; personal data, never copied into events (ADR 0006 §4)
    note                VARCHAR(500),
    failure_reason_code VARCHAR(40),
    event_id            UUID         NOT NULL,
    occurred_at         TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uq_parcel_status_history_sequence UNIQUE (parcel_id, sequence),
    CONSTRAINT uq_parcel_status_history_event_id UNIQUE (event_id),
    CONSTRAINT ck_parcel_status_history_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_parcel_status_history_from_status CHECK (from_status IN
        ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY')),
    CONSTRAINT ck_parcel_status_history_to_status CHECK (to_status IN
        ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED')),
    -- The first entry, and only the first, is the creation
    CONSTRAINT ck_parcel_status_history_creation CHECK (
        (sequence = 1) = (from_status IS NULL AND to_status = 'CREATED')),
    CONSTRAINT ck_parcel_status_history_actor_role CHECK (actor_role IN
        ('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER_ADMIN')),
    CONSTRAINT ck_parcel_status_history_failure_reason CHECK (failure_reason_code IN
        ('RECIPIENT_UNAVAILABLE', 'ADDRESS_NOT_FOUND', 'REFUSED', 'DAMAGED', 'OTHER')),
    -- FAILED needs a reason, and only FAILED has one (ADR 0006 §4)
    CONSTRAINT ck_parcel_status_history_failed_reason CHECK (
        (to_status = 'FAILED') = (failure_reason_code IS NOT NULL))
);
