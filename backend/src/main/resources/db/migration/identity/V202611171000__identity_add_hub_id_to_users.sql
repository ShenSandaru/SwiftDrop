-- Owner: identity module (ADR 0003). Assigns a dispatcher (and future hub-scoped staff) to a hub.
-- No cross-module foreign key to hubs (ADR 0003).

ALTER TABLE users ADD COLUMN hub_id UUID NULL;

CREATE INDEX ix_users_hub_id ON users (hub_id) WHERE hub_id IS NOT NULL;

