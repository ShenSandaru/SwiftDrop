-- Owner: delivery module (ADR 0003). The hub network of ADR 0009 §1: one branch hub per pricing
-- zone and exactly one central hub. Hubs are never deleted while parcels reference them; they are
-- deactivated instead.

CREATE TABLE hubs (
    id         UUID         PRIMARY KEY,
    -- Immutable: recorded in parcel history and events
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(100) NOT NULL,
    city       VARCHAR(100) NOT NULL,
    -- The pricing zone this hub serves as its branch; no foreign key across modules (ADR 0003)
    zone_id    UUID,
    central    BOOLEAN      NOT NULL DEFAULT FALSE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_hubs_code UNIQUE (code),
    CONSTRAINT uq_hubs_name UNIQUE (name),
    -- One branch per zone; several hubs without a zone are allowed only as the central hub
    CONSTRAINT uq_hubs_zone UNIQUE (zone_id),
    CONSTRAINT ck_hubs_code CHECK (code ~ '^[A-Z0-9-]{2,20}$'),
    -- Every hub is a branch of a zone, the central hub, or both
    CONSTRAINT ck_hubs_role CHECK (central OR zone_id IS NOT NULL)
);

-- Exactly one hub is central (ADR 0009 §1)
CREATE UNIQUE INDEX uq_hubs_single_central ON hubs (central) WHERE central;
