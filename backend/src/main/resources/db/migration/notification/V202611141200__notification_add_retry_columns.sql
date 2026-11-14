-- Add retry tracking columns for failed EMAIL and SMS notification deliveries.
ALTER TABLE notification_records
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMPTZ NULL;

CREATE INDEX ix_notification_records_retry_eligible
    ON notification_records (next_retry_at)
    WHERE delivery_status = 'FAILED' AND channel IN ('EMAIL', 'SMS');

