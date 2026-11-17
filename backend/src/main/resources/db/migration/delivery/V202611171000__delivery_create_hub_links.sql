-- Owner: delivery module (ADR 0003). Direct links between branch hubs (ADR 0011 §1): parcels
-- between two linked branches skip the central hub.

CREATE TABLE hub_links (
    id            UUID        PRIMARY KEY,
    first_hub_id  UUID        NOT NULL,
    second_hub_id UUID        NOT NULL,
    -- The admin who created the link; no foreign key across modules (ADR 0003)
    created_by    UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,

    -- A link goes away with either of its hubs
    CONSTRAINT fk_hub_links_first FOREIGN KEY (first_hub_id) REFERENCES hubs (id) ON DELETE CASCADE,
    CONSTRAINT fk_hub_links_second FOREIGN KEY (second_hub_id) REFERENCES hubs (id) ON DELETE CASCADE,
    CONSTRAINT uq_hub_links_pair UNIQUE (first_hub_id, second_hub_id),
    -- A link has no direction: the pair is stored once, smaller ID first, and never joins a hub to itself
    CONSTRAINT ck_hub_links_order CHECK (first_hub_id < second_hub_id)
);

-- Lookups by the second hub (the unique constraint covers the first)
CREATE INDEX ix_hub_links_second ON hub_links (second_hub_id);
