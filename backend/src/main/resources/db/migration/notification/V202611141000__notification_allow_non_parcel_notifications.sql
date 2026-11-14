-- Migration: Allow non-parcel notifications in notification_records
-- Author: Notification Module (ADR 0003, ADR 0004)
-- Date: 2026-11-14
--
-- Permits in-app notifications for account lifecycle events (e.g. merchant status changes)
-- which do not originate from a specific parcel or require a tracking number.

-- 1. Relax NOT NULL constraint on parcel_id and tracking_number
ALTER TABLE notification_records 
    ALTER COLUMN parcel_id DROP NOT NULL,
    ALTER COLUMN tracking_number DROP NOT NULL;

-- 2. Update tracking number format constraint to apply only when tracking_number is populated
ALTER TABLE notification_records 
    DROP CONSTRAINT ck_notification_records_tracking_number;

ALTER TABLE notification_records 
    ADD CONSTRAINT ck_notification_records_tracking_number 
        CHECK (tracking_number IS NULL OR tracking_number ~ '^SD[0-9A-HJKMNP-TV-Z]{10}$');

