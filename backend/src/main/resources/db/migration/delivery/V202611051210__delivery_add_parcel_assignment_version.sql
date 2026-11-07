-- SD-317: Add version column to parcel_assignments for optimistic locking
ALTER TABLE parcel_assignments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

