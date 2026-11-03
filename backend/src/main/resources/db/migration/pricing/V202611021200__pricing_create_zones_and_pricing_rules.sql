-- Owner: pricing module (ADR 0003). Delivery zones and pricing rules for fee calculation.

CREATE TABLE zones (
    id         UUID         PRIMARY KEY,
    code       VARCHAR(50)  NOT NULL,
    name       VARCHAR(200) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_zones_code UNIQUE (code),
    CONSTRAINT uq_zones_name UNIQUE (name)
);

CREATE INDEX ix_zones_active ON zones (active);

CREATE TABLE pricing_rules (
    id                    UUID           PRIMARY KEY,
    origin_zone_id        UUID           NOT NULL,
    destination_zone_id   UUID           NOT NULL,
    base_fee              NUMERIC(12,2)  NOT NULL,
    per_kg_fee            NUMERIC(12,2)  NOT NULL,
    small_size_surcharge  NUMERIC(12,2)  NOT NULL DEFAULT 0.00,
    medium_size_surcharge NUMERIC(12,2)  NOT NULL DEFAULT 0.00,
    large_size_surcharge  NUMERIC(12,2)  NOT NULL DEFAULT 0.00,
    currency              CHAR(3)        NOT NULL DEFAULT 'LKR',
    active                BOOLEAN        NOT NULL DEFAULT TRUE,
    version               BIGINT         NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_pricing_rules_origin_zone FOREIGN KEY (origin_zone_id) REFERENCES zones (id),
    CONSTRAINT fk_pricing_rules_destination_zone FOREIGN KEY (destination_zone_id) REFERENCES zones (id),
    CONSTRAINT uq_pricing_rules_origin_destination UNIQUE (origin_zone_id, destination_zone_id),
    CONSTRAINT ck_pricing_rules_base_fee CHECK (base_fee >= 0),
    CONSTRAINT ck_pricing_rules_per_kg_fee CHECK (per_kg_fee >= 0),
    CONSTRAINT ck_pricing_rules_small_surcharge CHECK (small_size_surcharge >= 0),
    CONSTRAINT ck_pricing_rules_medium_surcharge CHECK (medium_size_surcharge >= 0),
    CONSTRAINT ck_pricing_rules_large_surcharge CHECK (large_size_surcharge >= 0)
);

CREATE INDEX ix_pricing_rules_active ON pricing_rules (active);
CREATE INDEX ix_pricing_rules_origin_zone ON pricing_rules (origin_zone_id);
CREATE INDEX ix_pricing_rules_destination_zone ON pricing_rules (destination_zone_id);

