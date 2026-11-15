-- Owner: merchant module (ADR 0003). Consumer of parcel status events (SD-410, ADR 0004 §4.3, §7.1):
-- per-parcel processing state, processed and pending events, quarantined anomalies, and the webhook
-- delivery records the sender (SD-411) works through. Payloads are TEXT, not JSONB, so the stored event
-- envelope is exactly what is signed and sent.

-- Last applied sequence per merchant parcel; locked while an event of that parcel is handled
CREATE TABLE merchant_event_states (
    parcel_id             UUID        PRIMARY KEY,
    merchant_id           UUID        NOT NULL,
    last_applied_sequence INTEGER     NOT NULL DEFAULT 0,
    stuck                 BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at            TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_merchant_event_states_sequence CHECK (last_applied_sequence >= 0)
);

CREATE INDEX ix_merchant_event_states_merchant ON merchant_event_states (merchant_id);

-- Event IDs already applied; one event per parcel and sequence
CREATE TABLE merchant_processed_events (
    event_id     UUID        PRIMARY KEY,
    parcel_id    UUID        NOT NULL REFERENCES merchant_event_states (parcel_id),
    sequence     INTEGER     NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_merchant_processed_events_sequence UNIQUE (parcel_id, sequence),
    CONSTRAINT ck_merchant_processed_events_sequence CHECK (sequence >= 1)
);

-- Retention deletes old IDs in batches
CREATE INDEX ix_merchant_processed_events_processed_at ON merchant_processed_events (processed_at);

-- Events that arrived before an earlier sequence of their parcel; deleted once applied
CREATE TABLE merchant_pending_events (
    event_id           UUID        PRIMARY KEY,
    parcel_id          UUID        NOT NULL REFERENCES merchant_event_states (parcel_id),
    sequence           INTEGER     NOT NULL,
    payload            TEXT        NOT NULL,
    received_at        TIMESTAMPTZ NOT NULL,
    reconcile_attempts INTEGER     NOT NULL DEFAULT 0,
    next_reconcile_at  TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_merchant_pending_events_sequence UNIQUE (parcel_id, sequence),
    CONSTRAINT ck_merchant_pending_events_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_merchant_pending_events_attempts CHECK (reconcile_attempts >= 0)
);

CREATE INDEX ix_merchant_pending_events_due ON merchant_pending_events (next_reconcile_at);

-- Events that were not applied because they contradict what is stored; kept for review
CREATE TABLE merchant_event_anomalies (
    id          UUID         PRIMARY KEY,
    event_id    UUID         NOT NULL,
    parcel_id   UUID,
    sequence    INTEGER,
    kind        VARCHAR(30)  NOT NULL,
    detail      VARCHAR(200) NOT NULL,
    payload     TEXT         NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_merchant_event_anomalies_kind CHECK (kind IN
        ('IDENTITY_CONFLICT', 'SEQUENCE_CONFLICT', 'OWNERSHIP_MISMATCH', 'UNKNOWN_DUPLICATE'))
);

CREATE INDEX ix_merchant_event_anomalies_received_at ON merchant_event_anomalies (received_at);

-- One outgoing webhook per event and endpoint; sent by the webhook sender (SD-411)
CREATE TABLE merchant_webhook_deliveries (
    id                  UUID        PRIMARY KEY,
    endpoint_id         UUID        NOT NULL REFERENCES merchant_webhook_endpoints (id),
    merchant_id         UUID        NOT NULL,
    event_id            UUID        NOT NULL,
    parcel_id           UUID        NOT NULL,
    sequence            INTEGER     NOT NULL,
    event_type          VARCHAR(30) NOT NULL,
    payload             TEXT        NOT NULL,
    status              VARCHAR(30) NOT NULL,
    attempts            INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ NOT NULL,
    manual_redeliveries INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL,
    completed_at        TIMESTAMPTZ,

    CONSTRAINT uq_merchant_webhook_deliveries_event_endpoint UNIQUE (event_id, endpoint_id),
    CONSTRAINT ck_merchant_webhook_deliveries_status CHECK (status IN
        ('PENDING', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'SECRET_UNAVAILABLE')),
    CONSTRAINT ck_merchant_webhook_deliveries_sequence CHECK (sequence >= 1),
    CONSTRAINT ck_merchant_webhook_deliveries_attempts CHECK (attempts >= 0 AND manual_redeliveries >= 0)
);

-- The sender claims due records
CREATE INDEX ix_merchant_webhook_deliveries_due ON merchant_webhook_deliveries (status, next_attempt_at);
CREATE INDEX ix_merchant_webhook_deliveries_merchant ON merchant_webhook_deliveries (merchant_id, created_at);
