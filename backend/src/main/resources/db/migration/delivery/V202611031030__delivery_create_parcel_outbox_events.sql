-- Owner: delivery module (ADR 0003). Transactional outbox (ADR 0004 §3): each row is written in the
-- same transaction as its status change and published to RabbitMQ by the relay. Rows are never
-- deleted while unpublished.

CREATE TABLE parcel_outbox_events (
    -- Identity order is the publication order (ADR 0004 §4.1)
    id                    BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id              UUID         NOT NULL,
    parcel_id             UUID         NOT NULL REFERENCES parcels (id),
    sequence              INTEGER      NOT NULL,
    event_type            VARCHAR(100) NOT NULL,
    -- The serialized parcel.status-changed envelope; contains no personal data
    payload               JSONB        NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    published_at          TIMESTAMPTZ,
    attempts              INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at       TIMESTAMPTZ  NOT NULL,
    last_failure_category VARCHAR(40),

    CONSTRAINT uq_parcel_outbox_events_event_id UNIQUE (event_id),
    CONSTRAINT uq_parcel_outbox_events_sequence UNIQUE (parcel_id, sequence),
    CONSTRAINT ck_parcel_outbox_events_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_parcel_outbox_events_attempts CHECK (attempts >= 0)
);

-- The relay reads unpublished rows in outbox order
CREATE INDEX ix_parcel_outbox_events_unpublished ON parcel_outbox_events (id)
    WHERE published_at IS NULL;
