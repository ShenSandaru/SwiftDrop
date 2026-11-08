-- Owner: merchant module (Member B, ADR 0003, ADR 0008).
-- Merchant API keys: only the public prefix and the SHA-256 hash of the full key are stored.

CREATE TABLE merchant_api_keys (
    id                 UUID         PRIMARY KEY,
    merchant_id        UUID         NOT NULL REFERENCES merchants (id),
    key_prefix         VARCHAR(12)  NOT NULL,
    key_hash           CHAR(64)     NOT NULL,
    label              VARCHAR(100) NOT NULL,
    created_by_user_id UUID         NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    last_used_at       TIMESTAMPTZ,
    revoked_at         TIMESTAMPTZ,
    CONSTRAINT uq_merchant_api_keys_key_prefix UNIQUE (key_prefix),
    CONSTRAINT ck_merchant_api_keys_key_prefix CHECK (key_prefix ~ '^[a-z0-9]{12}$'),
    CONSTRAINT ck_merchant_api_keys_key_hash CHECK (key_hash ~ '^[0-9a-f]{64}$')
);

-- Key list per merchant and the active-key limit
CREATE INDEX ix_merchant_api_keys_merchant_id ON merchant_api_keys (merchant_id);
