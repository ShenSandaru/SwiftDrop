-- Owner: delivery module (ADR 0003).
-- Seed all 25 Sri Lankan district hubs on startup, with Colombo (CMB) as the central hub.
-- Idempotent so existing databases with manually added hubs are preserved without conflicts.

-- 1. Central hub: Colombo Branch (CMB)
INSERT INTO hubs (id, code, name, city, zone_id, central, active, created_at, updated_at, version)
SELECT gen_random_uuid(), d.code, d.default_branch_name, d.name, NULL, TRUE, TRUE, NOW(), NOW(), 0
FROM districts d
WHERE d.code = 'CMB'
  AND NOT EXISTS (
      SELECT 1
      FROM hubs h
      WHERE h.central = TRUE
         OR h.code = d.code
         OR h.name = d.default_branch_name
  )
ON CONFLICT DO NOTHING;

-- 2. Regional district branch hubs (24 remaining districts)
INSERT INTO hubs (id, code, name, city, zone_id, central, active, created_at, updated_at, version)
SELECT gen_random_uuid(), d.code, d.default_branch_name, d.name, z.id, FALSE, TRUE, NOW(), NOW(), 0
FROM districts d
JOIN zones z ON z.code = d.code
WHERE d.code <> 'CMB'
  AND NOT EXISTS (
      SELECT 1
      FROM hubs h
      WHERE h.code = d.code
         OR h.name = d.default_branch_name
         OR h.zone_id = z.id
  )
ON CONFLICT DO NOTHING;

-- 3. Backfill zones.hub_id for primary district zones linked to their branch hub
UPDATE zones z
SET hub_id = h.id
FROM hubs h
WHERE h.zone_id = z.id
  AND z.hub_id IS NULL;
