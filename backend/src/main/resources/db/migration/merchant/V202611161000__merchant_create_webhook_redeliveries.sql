-- Owner: merchant module (ADR 0003). Manual webhook redelivery (SD-412, ADR 0004 §7.5): an audit row per
-- redelivery, and attempt series on records and attempts. Series 0 is the automatic series; series 1 to 3 are
-- the manual redeliveries, each with its own attempt limit. Attempt numbers keep increasing across series.

CREATE TABLE merchant_webhook_redeliveries (
    id                   UUID        PRIMARY KEY,
    -- Restrictive: a delivery record with redelivery history cannot be deleted
    delivery_id          UUID        NOT NULL REFERENCES merchant_webhook_deliveries (id) ON DELETE RESTRICT,
    series               INTEGER     NOT NULL,
    requested_by_user_id UUID        NOT NULL,
    requested_by_role    VARCHAR(20) NOT NULL,
    previous_status      VARCHAR(30) NOT NULL,
    requested_at         TIMESTAMPTZ NOT NULL,

    CONSTRAINT uq_merchant_webhook_redeliveries_series UNIQUE (delivery_id, series),
    CONSTRAINT ck_merchant_webhook_redeliveries_series CHECK (series BETWEEN 1 AND 3),
    CONSTRAINT ck_merchant_webhook_redeliveries_role CHECK (requested_by_role IN ('MERCHANT', 'ADMIN')),
    CONSTRAINT ck_merchant_webhook_redeliveries_previous CHECK (previous_status IN ('FAILED', 'SECRET_UNAVAILABLE'))
);

ALTER TABLE merchant_webhook_deliveries
    -- Attempts made before the current series; the attempt limit counts from here
    ADD COLUMN series_start_attempts INTEGER NOT NULL DEFAULT 0,
    -- The user who started the current manual series, copied to its attempts
    ADD COLUMN series_triggered_by   UUID,
    ADD CONSTRAINT ck_merchant_webhook_deliveries_redeliveries CHECK (manual_redeliveries BETWEEN 0 AND 3),
    ADD CONSTRAINT ck_merchant_webhook_deliveries_series_start CHECK (series_start_attempts BETWEEN 0 AND attempts);

ALTER TABLE merchant_webhook_attempts
    ADD COLUMN series INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_merchant_webhook_attempts_series CHECK (series BETWEEN 0 AND 3);

-- Merchant history pages: newest first, stable on equal timestamps (replaces the index of V202611141000)
DROP INDEX ix_merchant_webhook_deliveries_merchant;
CREATE INDEX ix_merchant_webhook_deliveries_history ON merchant_webhook_deliveries (merchant_id, created_at DESC, id DESC);
