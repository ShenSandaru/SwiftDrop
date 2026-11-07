-- Owner: merchant module (Member A, ADR 0003, docs/merchant-contracts.md).
-- Append-only audit log for merchant approval lifecycle transitions.

CREATE TABLE merchant_status_history (
    id            UUID         PRIMARY KEY,
    merchant_id   UUID         NOT NULL REFERENCES merchants (id) ON DELETE CASCADE,
    from_status   VARCHAR(20),
    to_status     VARCHAR(20)  NOT NULL,
    actor_user_id UUID         REFERENCES users (id),
    reason        VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT ck_merchant_status_history_to_status CHECK (to_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT ck_merchant_status_history_from_status CHECK (from_status IS NULL OR from_status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

-- Historical status lookup for a merchant, oldest to newest
CREATE INDEX ix_merchant_status_history_merchant_created ON merchant_status_history (merchant_id, created_at);

