-- Owner: merchant module (ADR 0003). Webhook subscriptions accept the parcel statuses added by
-- ADR 0009 (hub network, delivery attempts, returns, cancellation). Only widens the allowed values:
-- existing subscriptions stay valid and are not subscribed to the new statuses automatically.

ALTER TABLE merchant_webhook_endpoint_events DROP CONSTRAINT ck_merchant_webhook_events_type;

ALTER TABLE merchant_webhook_endpoint_events ADD CONSTRAINT ck_merchant_webhook_events_type CHECK (event_type IN
    ('CREATED', 'PICKED_UP', 'AT_HUB', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERY_ATTEMPT_FAILED', 'DELIVERED',
     'RETURNING', 'RETURNED', 'CANCELLED', 'FAILED'));
