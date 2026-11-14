-- Owner: pricing module (ADR 0003).
-- Add GPS coordinates (latitude, longitude) to districts for distance-based line-haul fee calculations.

ALTER TABLE districts ADD COLUMN IF NOT EXISTS latitude NUMERIC(9,6);
ALTER TABLE districts ADD COLUMN IF NOT EXISTS longitude NUMERIC(9,6);

UPDATE districts SET latitude = 6.927079, longitude = 79.861244 WHERE code = 'CMB'; -- Colombo
UPDATE districts SET latitude = 7.084000, longitude = 79.994300 WHERE code = 'GAM'; -- Gampaha
UPDATE districts SET latitude = 6.585400, longitude = 79.960700 WHERE code = 'KAL'; -- Kalutara
UPDATE districts SET latitude = 7.290572, longitude = 80.633728 WHERE code = 'KDY'; -- Kandy
UPDATE districts SET latitude = 7.467500, longitude = 80.623400 WHERE code = 'MTL'; -- Matale
UPDATE districts SET latitude = 6.949700, longitude = 80.789100 WHERE code = 'NUE'; -- Nuwara Eliya
UPDATE districts SET latitude = 6.053500, longitude = 80.221000 WHERE code = 'GAL'; -- Galle
UPDATE districts SET latitude = 5.954920, longitude = 80.554955 WHERE code = 'MAT'; -- Matara
UPDATE districts SET latitude = 6.142900, longitude = 81.121200 WHERE code = 'HAM'; -- Hambantota
UPDATE districts SET latitude = 9.661498, longitude = 80.025543 WHERE code = 'JAF'; -- Jaffna
UPDATE districts SET latitude = 9.380300, longitude = 80.377000 WHERE code = 'KIL'; -- Kilinochchi
UPDATE districts SET latitude = 8.981000, longitude = 79.904400 WHERE code = 'MAN'; -- Mannar
UPDATE districts SET latitude = 8.754200, longitude = 80.498200 WHERE code = 'VAV'; -- Vavuniya
UPDATE districts SET latitude = 9.267100, longitude = 80.814200 WHERE code = 'MUL'; -- Mullaitivu
UPDATE districts SET latitude = 7.731000, longitude = 81.674700 WHERE code = 'BAT'; -- Batticaloa
UPDATE districts SET latitude = 7.291200, longitude = 81.672400 WHERE code = 'AMP'; -- Ampara
UPDATE districts SET latitude = 8.587400, longitude = 81.215200 WHERE code = 'TRI'; -- Trincomalee
UPDATE districts SET latitude = 7.486300, longitude = 80.362300 WHERE code = 'KUR'; -- Kurunegala
UPDATE districts SET latitude = 8.040800, longitude = 79.839400 WHERE code = 'PUT'; -- Puttalam
UPDATE districts SET latitude = 8.311400, longitude = 80.403700 WHERE code = 'ANU'; -- Anuradhapura
UPDATE districts SET latitude = 7.940300, longitude = 81.018800 WHERE code = 'POL'; -- Polonnaruwa
UPDATE districts SET latitude = 6.993400, longitude = 81.055000 WHERE code = 'BAD'; -- Badulla
UPDATE districts SET latitude = 6.872800, longitude = 81.350700 WHERE code = 'MON'; -- Monaragala
UPDATE districts SET latitude = 6.682800, longitude = 80.403400 WHERE code = 'RAT'; -- Ratnapura
UPDATE districts SET latitude = 7.251300, longitude = 80.346400 WHERE code = 'KEG'; -- Kegalle

