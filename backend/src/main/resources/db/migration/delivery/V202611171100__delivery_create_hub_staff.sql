-- Owner: delivery module (ADR 0003). Home hubs of dispatchers and couriers (ADR 0012 §1). A row
-- without a hub marks a head office dispatcher, who works country-wide.

CREATE TABLE hub_staff (
    id         UUID        PRIMARY KEY,
    -- A DISPATCHER or COURIER account; no foreign key across modules (ADR 0003)
    user_id    UUID        NOT NULL,
    -- NULL: head office
    hub_id     UUID,
    -- The admin who created the row; NULL for the rows this migration creates
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL,

    -- Staff rows go away with their hub
    CONSTRAINT fk_hub_staff_hub FOREIGN KEY (hub_id) REFERENCES hubs (id) ON DELETE CASCADE,
    CONSTRAINT uq_hub_staff_user_hub UNIQUE (user_id, hub_id)
);

-- A unique constraint does not compare NULLs, so head office needs its own rule: once per user
CREATE UNIQUE INDEX uq_hub_staff_head_office ON hub_staff (user_id) WHERE hub_id IS NULL;
CREATE INDEX ix_hub_staff_hub ON hub_staff (hub_id);

-- Dispatchers worked country-wide until now. Marking them as head office keeps that on the day
-- this is deployed; an admin then narrows them to their hubs (ADR 0012 §2). A one-time read of
-- identity's table: no foreign key and no later dependency on it.
INSERT INTO hub_staff (id, user_id, hub_id, created_by, created_at)
SELECT gen_random_uuid(), ur.user_id, NULL, NULL, now()
FROM user_roles ur
WHERE ur.role_name = 'DISPATCHER';
