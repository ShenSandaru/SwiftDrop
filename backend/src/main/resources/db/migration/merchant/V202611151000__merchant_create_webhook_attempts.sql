-- Owner: merchant module (ADR 0003). Webhook sender (SD-411, ADR 0004 §7.1): one row per attempt of a
-- delivery record, without bodies, headers, signatures or secrets; plus the sender's lease and the last
-- outcome on the delivery record.

CREATE TABLE merchant_webhook_attempts (
    id                   UUID        PRIMARY KEY,
    delivery_id          UUID        NOT NULL REFERENCES merchant_webhook_deliveries (id),
    attempt_number       INTEGER     NOT NULL,
    attempted_at         TIMESTAMPTZ NOT NULL,
    outcome              VARCHAR(30) NOT NULL,
    http_status          INTEGER,
    duration_ms          INTEGER     NOT NULL,
    -- Set for manual redeliveries (SD-412)
    triggered_by_user_id UUID,

    CONSTRAINT uq_merchant_webhook_attempts_number UNIQUE (delivery_id, attempt_number),
    CONSTRAINT ck_merchant_webhook_attempts_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_merchant_webhook_attempts_duration CHECK (duration_ms >= 0),
    CONSTRAINT ck_merchant_webhook_attempts_outcome CHECK (outcome IN
        ('SUCCEEDED', 'HTTP_ERROR', 'TIMEOUT', 'NETWORK_ERROR', 'REFUSED_DESTINATION', 'ENDPOINT_INACTIVE',
         'MERCHANT_NOT_APPROVED', 'MERCHANT_NOT_FOUND'))
);

-- The unique constraint above also serves lookups by delivery_id

ALTER TABLE merchant_webhook_deliveries
    ADD COLUMN last_outcome     VARCHAR(30),
    ADD COLUMN last_http_status INTEGER,
    -- The claim of one sender: a fresh token per claim, valid until lease_expires_at
    ADD COLUMN lease_token      UUID,
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_merchant_webhook_deliveries_lease
        CHECK ((status = 'IN_PROGRESS') = (lease_token IS NOT NULL AND lease_expires_at IS NOT NULL));

-- Claims of expired leases
CREATE INDEX ix_merchant_webhook_deliveries_lease ON merchant_webhook_deliveries (lease_expires_at)
    WHERE status = 'IN_PROGRESS';
