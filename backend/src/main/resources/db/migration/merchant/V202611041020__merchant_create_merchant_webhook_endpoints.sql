-- Owner: merchant module (Member A, ADR 0003, docs/merchant-contracts.md).
-- Merchant webhook subscriptions with AES-GCM encrypted signing secret storage and soft-delete support.

CREATE TABLE merchant_webhook_endpoints (
    id                 UUID          PRIMARY KEY,
    merchant_id        UUID          NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    url                VARCHAR(2048) NOT NULL,
    description        VARCHAR(255),
    enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
    secret_ciphertext  VARCHAR(512)  NOT NULL,
    secret_iv          VARCHAR(64)   NOT NULL,
    secret_key_version INTEGER       NOT NULL DEFAULT 1,
    secret_hint        VARCHAR(10)   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    deleted_at         TIMESTAMPTZ
);

-- Merchant endpoint list
CREATE INDEX ix_merchant_webhook_endpoints_merchant_id ON merchant_webhook_endpoints (merchant_id);

-- Partial index for active webhook delivery resolution
CREATE INDEX ix_merchant_webhook_endpoints_active ON merchant_webhook_endpoints (merchant_id)
    WHERE deleted_at IS NULL AND enabled = TRUE;

