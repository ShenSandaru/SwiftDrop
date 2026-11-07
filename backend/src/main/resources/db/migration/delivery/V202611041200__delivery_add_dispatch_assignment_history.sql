-- Owner: delivery module. Assignment records are parcel lifecycle state (ADR 0006).

CREATE TABLE parcel_assignments (
    id              UUID         PRIMARY KEY,
    parcel_id       UUID         NOT NULL REFERENCES parcels (id),
    courier_id      UUID         NOT NULL REFERENCES users (id),
    assignment_type VARCHAR(10)  NOT NULL,
    status          VARCHAR(12)  NOT NULL,
    assigned_at     TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ,
    -- Null only for legacy assignments whose dispatcher cannot be identified.
    dispatcher_id   UUID         REFERENCES users (id),

    CONSTRAINT ck_parcel_assignments_type CHECK (assignment_type IN ('PICKUP', 'DELIVERY')),
    CONSTRAINT ck_parcel_assignments_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'REASSIGNED')),
    CONSTRAINT ck_parcel_assignments_completion CHECK (
        (status = 'ACTIVE') = (completed_at IS NULL)
    )
);

-- Preserve the assignment that existed before history tracking was introduced. A missing
-- dispatcher identity is deliberately not inferred from created_by.
INSERT INTO parcel_assignments (
    id, parcel_id, courier_id, assignment_type, status, assigned_at, completed_at, dispatcher_id
)
SELECT gen_random_uuid(),
       id,
       assigned_courier_id,
       CASE WHEN status = 'CREATED' THEN 'PICKUP' ELSE 'DELIVERY' END,
       CASE WHEN status IN ('DELIVERED', 'FAILED') THEN 'COMPLETED' ELSE 'ACTIVE' END,
       assigned_at,
       CASE WHEN status IN ('DELIVERED', 'FAILED') THEN updated_at ELSE NULL END,
       NULL
FROM parcels
WHERE assigned_courier_id IS NOT NULL;

CREATE UNIQUE INDEX uq_parcel_assignments_active_parcel
    ON parcel_assignments (parcel_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_parcel_assignments_history
    ON parcel_assignments (parcel_id, assigned_at);
