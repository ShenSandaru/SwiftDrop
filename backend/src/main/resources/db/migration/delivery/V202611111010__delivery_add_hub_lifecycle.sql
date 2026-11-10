-- Owner: delivery module (ADR 0003). The hub lifecycle of ADR 0009 §2 to §5: a route fixed at
-- pickup, the hub a parcel is at or travels to, delivery attempts, returns and cancellation.
-- Parcels picked up before this migration have no route and keep the ADR 0006 rules.

-- Parcels: route, position in the network, attempts
ALTER TABLE parcels
    ADD COLUMN origin_hub_id       UUID REFERENCES hubs (id),
    ADD COLUMN via_hub_id          UUID REFERENCES hubs (id),
    ADD COLUMN destination_hub_id  UUID REFERENCES hubs (id),
    ADD COLUMN current_hub_id      UUID REFERENCES hubs (id),
    ADD COLUMN next_hub_id         UUID REFERENCES hubs (id),
    ADD COLUMN delivery_attempts   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_failure_reason VARCHAR(40);

ALTER TABLE parcels DROP CONSTRAINT ck_parcels_status;
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_status CHECK (status IN
    ('CREATED', 'PICKED_UP', 'AT_HUB', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPT_FAILED',
     'DELIVERED', 'RETURNING', 'RETURNED', 'CANCELLED', 'FAILED'));

-- The courier is now the current task's courier: empty between tasks (handoff at hubs, ADR 0008 §3)
ALTER TABLE parcels DROP CONSTRAINT ck_parcels_courier_after_created;

ALTER TABLE parcels ADD CONSTRAINT ck_parcels_delivery_attempts CHECK (delivery_attempts >= 0);
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_last_failure_reason CHECK (last_failure_reason IN
    ('RECIPIENT_UNAVAILABLE', 'ADDRESS_NOT_FOUND', 'REFUSED', 'DAMAGED', 'OTHER'));
-- A route is complete or absent
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_route CHECK (
    (origin_hub_id IS NULL) = (destination_hub_id IS NULL)
    AND (via_hub_id IS NULL OR origin_hub_id IS NOT NULL));
-- The current hub only while at a hub, the next hub only while travelling between hubs
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_current_hub CHECK (
    (current_hub_id IS NOT NULL) = (status = 'AT_HUB'));
ALTER TABLE parcels ADD CONSTRAINT ck_parcels_next_hub CHECK (
    next_hub_id IS NULL OR status = 'IN_TRANSIT');

-- Dispatch boards: parcels at a hub, and in transit to a hub
CREATE INDEX ix_parcels_current_hub ON parcels (current_hub_id) WHERE current_hub_id IS NOT NULL;
CREATE INDEX ix_parcels_next_hub ON parcels (next_hub_id) WHERE next_hub_id IS NOT NULL;

-- History: hub and attempt of each step, the new statuses, reasons for failed attempts
ALTER TABLE parcel_status_history
    ADD COLUMN hub_code VARCHAR(20),
    ADD COLUMN attempt  INTEGER;

ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_from_status;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_from_status CHECK (from_status IN
    ('CREATED', 'PICKED_UP', 'AT_HUB', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPT_FAILED',
     'RETURNING'));
ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_to_status;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_to_status CHECK (to_status IN
    ('CREATED', 'PICKED_UP', 'AT_HUB', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPT_FAILED',
     'DELIVERED', 'RETURNING', 'RETURNED', 'CANCELLED', 'FAILED'));
-- FAILED and a failed attempt need a reason, and only they have one (ADR 0009 §4)
ALTER TABLE parcel_status_history DROP CONSTRAINT ck_parcel_status_history_failed_reason;
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_failed_reason CHECK (
    (to_status IN ('FAILED', 'DELIVERY_ATTEMPT_FAILED')) = (failure_reason_code IS NOT NULL));
ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_attempt CHECK (
    attempt IS NULL OR attempt >= 1);

-- Courier tasks: return tasks, and tasks cancelled with their parcel (ADR 0009 §5)
ALTER TABLE parcel_assignments DROP CONSTRAINT ck_parcel_assignments_type;
ALTER TABLE parcel_assignments ADD CONSTRAINT ck_parcel_assignments_type CHECK (assignment_type IN
    ('PICKUP', 'DELIVERY', 'RETURN'));
ALTER TABLE parcel_assignments DROP CONSTRAINT ck_parcel_assignments_status;
ALTER TABLE parcel_assignments ADD CONSTRAINT ck_parcel_assignments_status CHECK (status IN
    ('ACTIVE', 'COMPLETED', 'REASSIGNED', 'CANCELLED'));

-- A courier's tasks finished on a day, for their run (D-09)
CREATE INDEX ix_parcel_assignments_courier_completed
    ON parcel_assignments (courier_id, completed_at)
    WHERE completed_at IS NOT NULL;
