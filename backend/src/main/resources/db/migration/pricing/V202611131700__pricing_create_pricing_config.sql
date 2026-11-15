-- Owner: pricing module (ADR 0003).
-- Global pricing tariff parameters and distance formula configuration.

CREATE TABLE pricing_config (
    id                    VARCHAR(50)   PRIMARY KEY,
    standard_base_fee     NUMERIC(12,2) NOT NULL,
    free_km_allowance     NUMERIC(6,2)  NOT NULL,
    rate_per_km           NUMERIC(12,2) NOT NULL,
    max_cap               NUMERIC(12,2) NOT NULL,
    per_kg_fee            NUMERIC(12,2) NOT NULL,
    small_size_surcharge  NUMERIC(12,2) NOT NULL,
    medium_size_surcharge NUMERIC(12,2) NOT NULL,
    large_size_surcharge  NUMERIC(12,2) NOT NULL,
    currency              CHAR(3)       NOT NULL DEFAULT 'LKR',
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Seed default global configuration for Sri Lanka
INSERT INTO pricing_config (
    id, standard_base_fee, free_km_allowance, rate_per_km, max_cap,
    per_kg_fee, small_size_surcharge, medium_size_surcharge, large_size_surcharge, currency
) VALUES (
    'DEFAULT', 350.00, 30.00, 1.33, 1000.00,
    50.00, 0.00, 50.00, 150.00, 'LKR'
) ON CONFLICT (id) DO NOTHING;

