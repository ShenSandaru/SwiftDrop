-- Owner: notification module (ADR 0003, ADR 0004 §4.3, docs/architecture/notification-database-design.md).
-- Tables for notification delivery, consumer sequence watermarks, and out-of-order event buffering.

CREATE TABLE notification_records (
    id                UUID         PRIMARY KEY,
    event_id          UUID         NOT NULL,
    recipient_user_id UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    parcel_id         UUID         NOT NULL,
    tracking_number   VARCHAR(16)  NOT NULL,
    title             VARCHAR(200) NOT NULL,
    message           TEXT         NOT NULL,
    channel           VARCHAR(20)  NOT NULL,
    
    -- User read state (primarily for IN_APP notifications)
    is_read           BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at           TIMESTAMPTZ,

    -- Channel delivery state (for external transports and audit)
    delivery_status   VARCHAR(20)  NOT NULL,
    sent_at           TIMESTAMPTZ,
    failure_reason    VARCHAR(500),

    created_at        TIMESTAMPTZ  NOT NULL,

    -- Idempotency: guarantees one notification record per event, channel, and recipient
    CONSTRAINT uq_notification_records_event_channel_user 
        UNIQUE (event_id, channel, recipient_user_id),

    -- Allowed channels
    CONSTRAINT ck_notification_records_channel 
        CHECK (channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH')),

    -- Allowed delivery statuses
    CONSTRAINT ck_notification_records_delivery_status 
        CHECK (delivery_status IN ('PENDING', 'SENT', 'FAILED', 'NOT_APPLICABLE')),

    -- State consistency: read_at must be populated if and only if is_read is TRUE
    CONSTRAINT ck_notification_records_read_consistency 
        CHECK ((is_read = TRUE) = (read_at IS NOT NULL)),

    -- Delivery consistency: sent_at must be populated if delivery_status is SENT
    CONSTRAINT ck_notification_records_delivery_consistency 
        CHECK (
            (delivery_status = 'SENT' AND sent_at IS NOT NULL)
            OR (delivery_status <> 'SENT')
        ),

    -- Tracking number format matches system standard (D-05)
    CONSTRAINT ck_notification_records_tracking_number 
        CHECK (tracking_number ~ '^SD[0-9A-HJKMNP-TV-Z]{10}$')
);

-- Index 1: Optimized user notification feed (newest first)
CREATE INDEX ix_notification_records_user_feed 
    ON notification_records (recipient_user_id, created_at DESC);

-- Index 2: Highly efficient partial index for unread notification count badge (IN_APP)
CREATE INDEX ix_notification_records_user_unread 
    ON notification_records (recipient_user_id) 
    WHERE channel = 'IN_APP' AND is_read = FALSE;

-- Index 3: Efficient lookup by parcel for support, audit, and verification queries
CREATE INDEX ix_notification_records_parcel_id 
    ON notification_records (parcel_id);

CREATE TABLE notification_consumer_state (
    parcel_id              UUID        PRIMARY KEY,
    last_applied_sequence  INTEGER     NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_notification_consumer_state_sequence 
        CHECK (last_applied_sequence >= 1)
);

CREATE TABLE notification_pending_events (
    event_id           UUID        PRIMARY KEY,
    parcel_id          UUID        NOT NULL,
    sequence           INTEGER     NOT NULL,
    payload            JSONB       NOT NULL,
    retry_count        INTEGER     NOT NULL DEFAULT 0,
    status             VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_reconciled_at TIMESTAMPTZ,
    received_at        TIMESTAMPTZ NOT NULL,

    -- Constraint: Only one pending event allowed per parcel sequence number
    CONSTRAINT uq_notification_pending_events_parcel_sequence 
        UNIQUE (parcel_id, sequence),
    CONSTRAINT ck_notification_pending_events_sequence 
        CHECK (sequence >= 1),
    CONSTRAINT ck_notification_pending_events_retry_count 
        CHECK (retry_count >= 0),
    CONSTRAINT ck_notification_pending_events_status 
        CHECK (status IN ('PENDING', 'UNRESOLVED'))
);

-- Index: Used by GapReconciliationScheduler to discover active pending events older than grace period
CREATE INDEX ix_notification_pending_events_reconciliation 
    ON notification_pending_events (received_at, parcel_id)
    WHERE status = 'PENDING';

