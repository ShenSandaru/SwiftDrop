-- Seed delivery zones, hubs, and pricing rules for local development and merchant order testing.

-- 1. Create Colombo and Kandy zones
INSERT INTO zones (id, code, name, active, version, created_at)
VALUES 
  ('00000000-0000-0000-0000-00000000c0b0', 'CMB', 'Colombo', true, 0, NOW()),
  ('00000000-0000-0000-0000-00000000ca4d', 'KDY', 'Kandy', true, 0, NOW())
ON CONFLICT (id) DO NOTHING;

-- 2. Create Hubs (Colombo as central hub, Kandy as branch hub)
INSERT INTO hubs (id, code, name, city, zone_id, central, active, created_at, updated_at)
VALUES 
  (gen_random_uuid(), 'CMB', 'Colombo Central Hub', 'Colombo', '00000000-0000-0000-0000-00000000c0b0', true, true, NOW(), NOW()),
  (gen_random_uuid(), 'KDY', 'Kandy Branch Hub', 'Kandy', '00000000-0000-0000-0000-00000000ca4d', false, true, NOW(), NOW())
ON CONFLICT (code) DO NOTHING;

-- 3. Create Pricing Rules (intra-Colombo, Colombo to Kandy, Kandy to Colombo)
INSERT INTO pricing_rules (id, origin_zone_id, destination_zone_id, base_fee, per_kg_fee, small_size_surcharge, medium_size_surcharge, large_size_surcharge, currency, active, version, created_at)
VALUES
  (gen_random_uuid(), '00000000-0000-0000-0000-00000000c0b0', '00000000-0000-0000-0000-00000000c0b0', 350.00, 50.00, 0.00, 50.00, 150.00, 'LKR', true, 0, NOW()),
  (gen_random_uuid(), '00000000-0000-0000-0000-00000000c0b0', '00000000-0000-0000-0000-00000000ca4d', 500.00, 75.00, 0.00, 50.00, 150.00, 'LKR', true, 0, NOW()),
  (gen_random_uuid(), '00000000-0000-0000-0000-00000000ca4d', '00000000-0000-0000-0000-00000000c0b0', 500.00, 75.00, 0.00, 50.00, 150.00, 'LKR', true, 0, NOW())
ON CONFLICT (origin_zone_id, destination_zone_id) DO NOTHING;
