-- Owner: delivery module (ADR 0003). Removes the head office scope again (ADR 0012 §2): admins are
-- the only staff who work everywhere, and every dispatcher or courier works for named hubs.
--
-- The rows V202611171100 created for existing dispatchers (a row with no hub) are deleted, so those
-- dispatchers see nothing until an admin gives them a hub. A new migration rather than an edit of
-- V202611171100, because a database that already ran it must not see its checksum change.

DELETE FROM hub_staff WHERE hub_id IS NULL;

DROP INDEX uq_hub_staff_head_office;

ALTER TABLE hub_staff ALTER COLUMN hub_id SET NOT NULL;
-- Only rows created by the migration had no admin; they are gone
ALTER TABLE hub_staff ALTER COLUMN created_by SET NOT NULL;
