-- Owner: merchant module (Member A, ADR 0003, docs/merchant-contracts.md).
-- Subscribed parcel-status event types per webhook endpoint.

CREATE TABLE merchant_webhook_endpoint_events (
    endpoint_id UUID        NOT NULL REFERENCES merchant_webhook_endpoints (id) ON DELETE CASCADE,
    event_type  VARCHAR(30) NOT NULL,

    PRIMARY KEY (endpoint_id, event_type),
    CONSTRAINT ck_merchant_webhook_events_type CHECK (event_type IN
        ('CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'))
);

