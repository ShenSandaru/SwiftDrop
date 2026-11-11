-- Owner: delivery module (ADR 0003). Preserves historical DISPATCHER_ADMIN audit records while allowing ADMIN and DISPATCHER.

ALTER TABLE parcel_status_history DROP CONSTRAINT IF EXISTS ck_parcel_status_history_actor_role;

ALTER TABLE parcel_status_history ADD CONSTRAINT ck_parcel_status_history_actor_role
CHECK (actor_role IN ('CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER', 'ADMIN', 'DISPATCHER_ADMIN'));

