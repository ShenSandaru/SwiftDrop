-- Owner: pricing module (ADR 0003).
-- Seed a primary delivery zone for every non-central Sri Lankan district so each regional hub
-- satisfies ck_hubs_role (central OR zone_id IS NOT NULL) out of the box.

INSERT INTO zones (id, code, name, active, version, created_at)
SELECT gen_random_uuid(), d.code, d.name, TRUE, 0, NOW()
FROM districts d
WHERE d.code <> 'CMB'
ON CONFLICT DO NOTHING;
