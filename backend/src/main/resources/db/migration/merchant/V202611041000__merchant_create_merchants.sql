-- Owner: merchant module (Member A, ADR 0003, docs/merchant-contracts.md).
-- Merchant business profiles with admin approval lifecycle and optimistic locking.

CREATE TABLE merchants (
    id                       UUID          PRIMARY KEY,
    user_id                  UUID          NOT NULL,
    business_name            VARCHAR(200)  NOT NULL,
    business_type            VARCHAR(50)   NOT NULL,
    business_registration_no VARCHAR(100)  NOT NULL,
    contact_phone            VARCHAR(20)   NOT NULL,
    business_address         VARCHAR(500)  NOT NULL,
    business_description     VARCHAR(500),
    website_url              VARCHAR(2048),
    status                   VARCHAR(20)   NOT NULL,
    status_reason            VARCHAR(500),
    decided_by               UUID          REFERENCES users (id),
    decided_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_merchants_user_id UNIQUE (user_id),
    CONSTRAINT uq_merchants_registration_no UNIQUE (business_registration_no),
    CONSTRAINT fk_merchants_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT ck_merchants_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED'))
);

-- Admin approval queue and status filtering
CREATE INDEX ix_merchants_status_created_at ON merchants (status, created_at);
