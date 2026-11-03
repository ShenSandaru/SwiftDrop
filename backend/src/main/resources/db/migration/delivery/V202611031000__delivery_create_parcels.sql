-- Owner: delivery module (ADR 0003). Parcels with embedded pickup and drop-off addresses (D-23).
-- Contact names, phones and address lines are personal data.

CREATE TABLE parcels (
    id                       UUID          PRIMARY KEY,
    -- 'SD' + 10 Crockford base32 characters, stored in upper case (D-05)
    tracking_number          VARCHAR(16)   NOT NULL,
    source                   VARCHAR(20)   NOT NULL,
    customer_id              UUID          REFERENCES users (id),
    -- No foreign key: delivery must not depend on the merchant module's tables (ADR 0003)
    merchant_id              UUID,
    merchant_order_reference VARCHAR(100),

    pickup_contact_name      VARCHAR(200)  NOT NULL,
    pickup_phone             VARCHAR(20)   NOT NULL,
    pickup_line1             VARCHAR(200)  NOT NULL,
    pickup_line2             VARCHAR(200),
    pickup_city              VARCHAR(100)  NOT NULL,
    -- Zone IDs belong to pricing; the foreign keys to zones are added once pricing is merged
    pickup_zone_id           UUID          NOT NULL,
    pickup_postal_code       VARCHAR(10),

    dropoff_contact_name     VARCHAR(200)  NOT NULL,
    dropoff_phone            VARCHAR(20)   NOT NULL,
    dropoff_line1            VARCHAR(200)  NOT NULL,
    dropoff_line2            VARCHAR(200),
    dropoff_city             VARCHAR(100)  NOT NULL,
    dropoff_zone_id          UUID          NOT NULL,
    dropoff_postal_code      VARCHAR(10),

    size                     VARCHAR(10)   NOT NULL,
    weight_grams             INTEGER       NOT NULL,
    description              VARCHAR(200),

    -- Fee snapshot taken at creation; later pricing changes never alter it (contract §8.2)
    fee_amount               NUMERIC(12,2) NOT NULL,
    fee_currency             CHAR(3)       NOT NULL,
    pricing_rule_id          UUID          NOT NULL,

    status                   VARCHAR(30)   NOT NULL,
    -- Sequence of the latest status event; CREATED is 1 (ADR 0004 §4.1)
    last_sequence            INTEGER       NOT NULL,
    assigned_courier_id      UUID          REFERENCES users (id),
    assigned_at              TIMESTAMPTZ,
    -- Null for parcels created through the merchant API, which has no user
    created_by               UUID          REFERENCES users (id),
    created_at               TIMESTAMPTZ   NOT NULL,
    updated_at               TIMESTAMPTZ   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT uq_parcels_tracking_number UNIQUE (tracking_number),
    CONSTRAINT uq_parcels_merchant_order_reference UNIQUE (merchant_id, merchant_order_reference),
    CONSTRAINT ck_parcels_tracking_number CHECK (tracking_number ~ '^SD[0-9A-HJKMNP-TV-Z]{10}$'),
    CONSTRAINT ck_parcels_source CHECK (source IN ('CUSTOMER', 'MERCHANT')),
    -- Exactly one owner, matching the source
    CONSTRAINT ck_parcels_owner CHECK (
        (source = 'CUSTOMER' AND customer_id IS NOT NULL
            AND merchant_id IS NULL AND merchant_order_reference IS NULL)
        OR (source = 'MERCHANT' AND merchant_id IS NOT NULL
            AND customer_id IS NULL AND merchant_order_reference IS NOT NULL)),
    CONSTRAINT ck_parcels_size CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT ck_parcels_weight_grams CHECK (weight_grams BETWEEN 1 AND 30000),
    CONSTRAINT ck_parcels_fee_amount CHECK (fee_amount >= 0),
    CONSTRAINT ck_parcels_status CHECK (status IN
        ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED')),
    CONSTRAINT ck_parcels_last_sequence CHECK (last_sequence >= 1),
    CONSTRAINT ck_parcels_assignment CHECK ((assigned_courier_id IS NULL) = (assigned_at IS NULL)),
    -- A courier must be assigned before PICKED_UP (ADR 0006 §1)
    CONSTRAINT ck_parcels_courier_after_created CHECK (status = 'CREATED' OR assigned_courier_id IS NOT NULL)
);

-- Courier lists and workload counts
CREATE INDEX ix_parcels_courier_status ON parcels (assigned_courier_id, status);
-- Customer history, newest first
CREATE INDEX ix_parcels_customer_created ON parcels (customer_id, created_at);
-- Merchant dashboard, newest first
CREATE INDEX ix_parcels_merchant_created ON parcels (merchant_id, created_at);
-- Unassigned parcels for dispatch, oldest first
CREATE INDEX ix_parcels_unassigned ON parcels (created_at)
    WHERE status = 'CREATED' AND assigned_courier_id IS NULL;
