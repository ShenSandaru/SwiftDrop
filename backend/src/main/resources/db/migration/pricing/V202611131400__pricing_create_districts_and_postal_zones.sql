-- Owner: pricing module (ADR 0003).
-- Reference catalog of Sri Lankan administrative districts and postal delivery zones.

CREATE TABLE districts (
    code                VARCHAR(10)  PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    province            VARCHAR(100) NOT NULL,
    postal_code         VARCHAR(10)  NOT NULL,
    default_branch_name VARCHAR(100) NOT NULL,
    CONSTRAINT uq_districts_name UNIQUE (name)
);

CREATE TABLE district_postal_zones (
    id            UUID         PRIMARY KEY,
    district_code VARCHAR(10)  NOT NULL,
    code          VARCHAR(50)  NOT NULL,
    name          VARCHAR(200) NOT NULL,
    postal_code   VARCHAR(10),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_district_postal_zones_district FOREIGN KEY (district_code) REFERENCES districts (code) ON DELETE CASCADE,
    CONSTRAINT uq_district_postal_zones_code UNIQUE (code)
);

CREATE INDEX ix_district_postal_zones_district ON district_postal_zones (district_code);

-- Link zones to hubs (multi-zone hubs)
ALTER TABLE zones ADD COLUMN IF NOT EXISTS hub_id UUID;
CREATE INDEX IF NOT EXISTS ix_zones_hub_id ON zones (hub_id);

-- Seed all 25 administrative districts
INSERT INTO districts (code, name, province, postal_code, default_branch_name) VALUES
('CMB', 'Colombo', 'Western', '00100', 'Colombo Branch'),
('GAM', 'Gampaha', 'Western', '11000', 'Gampaha Branch'),
('KAL', 'Kalutara', 'Western', '12000', 'Kalutara Branch'),
('KDY', 'Kandy', 'Central', '20000', 'Kandy Branch'),
('MTL', 'Matale', 'Central', '21000', 'Matale Branch'),
('NUE', 'Nuwara Eliya', 'Central', '22200', 'Nuwara Eliya Branch'),
('GAL', 'Galle', 'Southern', '80000', 'Galle Branch'),
('MAT', 'Matara', 'Southern', '81000', 'Matara Branch'),
('HAM', 'Hambantota', 'Southern', '82000', 'Hambantota Branch'),
('JAF', 'Jaffna', 'Northern', '40000', 'Jaffna Branch'),
('KIL', 'Kilinochchi', 'Northern', '44000', 'Kilinochchi Branch'),
('MAN', 'Mannar', 'Northern', '41000', 'Mannar Branch'),
('VAV', 'Vavuniya', 'Northern', '43000', 'Vavuniya Branch'),
('MUL', 'Mullaitivu', 'Northern', '42000', 'Mullaitivu Branch'),
('BAT', 'Batticaloa', 'Eastern', '30000', 'Batticaloa Branch'),
('AMP', 'Ampara', 'Eastern', '32000', 'Ampara Branch'),
('TRI', 'Trincomalee', 'Eastern', '31000', 'Trincomalee Branch'),
('KUR', 'Kurunegala', 'North Western', '60000', 'Kurunegala Branch'),
('PUT', 'Puttalam', 'North Western', '61000', 'Puttalam Branch'),
('ANU', 'Anuradhapura', 'North Central', '50000', 'Anuradhapura Branch'),
('POL', 'Polonnaruwa', 'North Central', '51000', 'Polonnaruwa Branch'),
('BAD', 'Badulla', 'Uva', '90000', 'Badulla Branch'),
('MON', 'Monaragala', 'Uva', '91000', 'Monaragala Branch'),
('RAT', 'Ratnapura', 'Sabaragamuwa', '70000', 'Ratnapura Branch'),
('KEG', 'Kegalle', 'Sabaragamuwa', '71000', 'Kegalle Branch');

-- Seed postal zones for Colombo
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'CMB', 'CMB-FRT', 'Colombo 01 - Fort', '00100'),
(gen_random_uuid(), 'CMB', 'CMB-SLI', 'Colombo 02 - Slave Island', '00200'),
(gen_random_uuid(), 'CMB', 'CMB-KLP', 'Colombo 03 - Kollupitiya', '00300'),
(gen_random_uuid(), 'CMB', 'CMB-BPT', 'Colombo 04 - Bambalapitiya', '00400'),
(gen_random_uuid(), 'CMB', 'CMB-HVT', 'Colombo 05 - Havelock Town', '00500'),
(gen_random_uuid(), 'CMB', 'CMB-WEL', 'Colombo 06 - Wellawatte', '00600'),
(gen_random_uuid(), 'CMB', 'CMB-CNG', 'Colombo 07 - Cinnamon Gardens', '00700'),
(gen_random_uuid(), 'CMB', 'CMB-BOR', 'Colombo 08 - Borella', '00800'),
(gen_random_uuid(), 'CMB', 'CMB-DMT', 'Colombo 09 - Dematagoda', '00900'),
(gen_random_uuid(), 'CMB', 'CMB-MRD', 'Colombo 10 - Maradana', '01000'),
(gen_random_uuid(), 'CMB', 'CMB-PET', 'Colombo 11 - Pettah', '01100'),
(gen_random_uuid(), 'CMB', 'CMB-HLT', 'Colombo 12 - Hultsdorf', '01200'),
(gen_random_uuid(), 'CMB', 'CMB-KTH', 'Colombo 13 - Kotahena', '01300'),
(gen_random_uuid(), 'CMB', 'CMB-GRP', 'Colombo 14 - Grandpass', '01400'),
(gen_random_uuid(), 'CMB', 'CMB-MTW', 'Colombo 15 - Mutwal', '01500'),
(gen_random_uuid(), 'CMB', 'CMB-DHW', 'Dehiwala', '10350'),
(gen_random_uuid(), 'CMB', 'CMB-MTL', 'Mount Lavinia', '10370'),
(gen_random_uuid(), 'CMB', 'CMB-MRT', 'Moratuwa', '10400'),
(gen_random_uuid(), 'CMB', 'CMB-NGD', 'Nugegoda', '10250'),
(gen_random_uuid(), 'CMB', 'CMB-MHG', 'Maharagama', '10280'),
(gen_random_uuid(), 'CMB', 'CMB-KTW', 'Kottawa', '10230'),
(gen_random_uuid(), 'CMB', 'CMB-BTR', 'Battaramulla', '10120'),
(gen_random_uuid(), 'CMB', 'CMB-RJG', 'Rajagiriya', '10107'),
(gen_random_uuid(), 'CMB', 'CMB-MLB', 'Malabe', '10115'),
(gen_random_uuid(), 'CMB', 'CMB-KDW', 'Kaduwela', '10640'),
(gen_random_uuid(), 'CMB', 'CMB-HMG', 'Homagama', '10200'),
(gen_random_uuid(), 'CMB', 'CMB-PLN', 'Piliyandala', '10300'),
(gen_random_uuid(), 'CMB', 'CMB-KSB', 'Kesbewa', '10305'),
(gen_random_uuid(), 'CMB', 'CMB-HNW', 'Hanwella', '10656'),
(gen_random_uuid(), 'CMB', 'CMB-AVS', 'Avissawella', '10700');

-- Seed postal zones for Gampaha
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'GAM', 'GAM-GMP', 'Gampaha Town', '11000'),
(gen_random_uuid(), 'GAM', 'GAM-NEG', 'Negombo', '11500'),
(gen_random_uuid(), 'GAM', 'GAM-KEL', 'Kelaniya', '11600'),
(gen_random_uuid(), 'GAM', 'GAM-WAT', 'Wattala', '11300'),
(gen_random_uuid(), 'GAM', 'GAM-JAE', 'Ja-Ela', '11350'),
(gen_random_uuid(), 'GAM', 'GAM-KAN', 'Kandana', '11320'),
(gen_random_uuid(), 'GAM', 'GAM-KAD', 'Kadawatha', '11850'),
(gen_random_uuid(), 'GAM', 'GAM-KIR', 'Kiribathgoda', '11600'),
(gen_random_uuid(), 'GAM', 'GAM-MIN', 'Minuwangoda', '11550'),
(gen_random_uuid(), 'GAM', 'GAM-MIR', 'Mirigama', '11200'),
(gen_random_uuid(), 'GAM', 'GAM-NIT', 'Nittambuwa', '11880'),
(gen_random_uuid(), 'GAM', 'GAM-BIY', 'Biyagama', '11650'),
(gen_random_uuid(), 'GAM', 'GAM-RAG', 'Ragama', '11010'),
(gen_random_uuid(), 'GAM', 'GAM-DIV', 'Divulapitiya', '11250'),
(gen_random_uuid(), 'GAM', 'GAM-KAT', 'Katunayake', '11450');

-- Seed postal zones for Kalutara
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'KAL', 'KAL-KAL', 'Kalutara Town', '12000'),
(gen_random_uuid(), 'KAL', 'KAL-PAN', 'Panadura', '12500'),
(gen_random_uuid(), 'KAL', 'KAL-HOR', 'Horana', '12400'),
(gen_random_uuid(), 'KAL', 'KAL-BER', 'Beruwala', '12070'),
(gen_random_uuid(), 'KAL', 'KAL-ALU', 'Aluthgama', '12080'),
(gen_random_uuid(), 'KAL', 'KAL-BAN', 'Bandaragama', '12530'),
(gen_random_uuid(), 'KAL', 'KAL-MAT', 'Matugama', '12100'),
(gen_random_uuid(), 'KAL', 'KAL-WAD', 'Wadduwa', '12560'),
(gen_random_uuid(), 'KAL', 'KAL-AGA', 'Agalawatta', '12200'),
(gen_random_uuid(), 'KAL', 'KAL-BUL', 'Bulathsinhala', '12300');

-- Seed postal zones for Kandy
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'KDY', 'KDY-KND', 'Kandy City', '20000'),
(gen_random_uuid(), 'KDY', 'KDY-PER', 'Peradeniya', '20400'),
(gen_random_uuid(), 'KDY', 'KDY-KAT', 'Katugastota', '20800'),
(gen_random_uuid(), 'KDY', 'KDY-AMP', 'Ampitiya', '20160'),
(gen_random_uuid(), 'KDY', 'KDY-DIG', 'Digana', '20180'),
(gen_random_uuid(), 'KDY', 'KDY-KUN', 'Kundasale', '20168'),
(gen_random_uuid(), 'KDY', 'KDY-GAM', 'Gampola', '20500'),
(gen_random_uuid(), 'KDY', 'KDY-NAW', 'Nawalapitiya', '20650'),
(gen_random_uuid(), 'KDY', 'KDY-AKU', 'Akurana', '20850'),
(gen_random_uuid(), 'KDY', 'KDY-PIL', 'Pilimathalawa', '20450'),
(gen_random_uuid(), 'KDY', 'KDY-KAD', 'Kadugannawa', '20300'),
(gen_random_uuid(), 'KDY', 'KDY-WAT', 'Wattegama', '20810'),
(gen_random_uuid(), 'KDY', 'KDY-TEL', 'Teldeniya', '20900');

-- Seed postal zones for Matale
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'MTL', 'MTL-MAT', 'Matale Town', '21000'),
(gen_random_uuid(), 'MTL', 'MTL-DAM', 'Dambulla', '21100'),
(gen_random_uuid(), 'MTL', 'MTL-GAL', 'Galewela', '21200'),
(gen_random_uuid(), 'MTL', 'MTL-UKU', 'Ukuwela', '21300'),
(gen_random_uuid(), 'MTL', 'MTL-RAT', 'Rattota', '21400'),
(gen_random_uuid(), 'MTL', 'MTL-SIG', 'Sigiriya', '21120'),
(gen_random_uuid(), 'MTL', 'MTL-NAU', 'Naula', '21210');

-- Seed postal zones for Nuwara Eliya
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'NUE', 'NUE-NUW', 'Nuwara Eliya Town', '22200'),
(gen_random_uuid(), 'NUE', 'NUE-HAT', 'Hatton', '22000'),
(gen_random_uuid(), 'NUE', 'NUE-TAL', 'Talawakele', '22100'),
(gen_random_uuid(), 'NUE', 'NUE-GIN', 'Ginigathena', '22080'),
(gen_random_uuid(), 'NUE', 'NUE-MAS', 'Maskeliya', '22070'),
(gen_random_uuid(), 'NUE', 'NUE-KOT', 'Kotagala', '22080'),
(gen_random_uuid(), 'NUE', 'NUE-RAG', 'Ragala', '22220'),
(gen_random_uuid(), 'NUE', 'NUE-WAL', 'Walapane', '22270');

-- Seed postal zones for Galle
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'GAL', 'GAL-GAL', 'Galle Fort / Town', '80000'),
(gen_random_uuid(), 'GAL', 'GAL-KAR', 'Karapitiya', '80000'),
(gen_random_uuid(), 'GAL', 'GAL-HIK', 'Hikkaduwa', '80240'),
(gen_random_uuid(), 'GAL', 'GAL-AMB', 'Ambalangoda', '80300'),
(gen_random_uuid(), 'GAL', 'GAL-BAD', 'Baddegama', '80200'),
(gen_random_uuid(), 'GAL', 'GAL-KOG', 'Koggala', '80130'),
(gen_random_uuid(), 'GAL', 'GAL-AHA', 'Ahangama', '80650'),
(gen_random_uuid(), 'GAL', 'GAL-BEN', 'Bentota', '80500'),
(gen_random_uuid(), 'GAL', 'GAL-ELP', 'Elpitiya', '80400'),
(gen_random_uuid(), 'GAL', 'GAL-BAL', 'Balapitiya', '80550'),
(gen_random_uuid(), 'GAL', 'GAL-NEL', 'Neluwa', '80082');

-- Seed postal zones for Matara
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'MAT', 'MAT-MAT', 'Matara Town', '81000'),
(gen_random_uuid(), 'MAT', 'MAT-WEL', 'Weligama', '81700'),
(gen_random_uuid(), 'MAT', 'MAT-MIR', 'Mirissa', '81740'),
(gen_random_uuid(), 'MAT', 'MAT-DIK', 'Dikwella', '81100'),
(gen_random_uuid(), 'MAT', 'MAT-AKU', 'Akuressa', '81300'),
(gen_random_uuid(), 'MAT', 'MAT-DEN', 'Deniyaya', '81500'),
(gen_random_uuid(), 'MAT', 'MAT-HAK', 'Hakmana', '81330'),
(gen_random_uuid(), 'MAT', 'MAT-KAM', 'Kamburupitiya', '81050'),
(gen_random_uuid(), 'MAT', 'MAT-GAN', 'Gandara', '81170');

-- Seed postal zones for Hambantota
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'HAM', 'HAM-HAM', 'Hambantota Town', '82000'),
(gen_random_uuid(), 'HAM', 'HAM-TAN', 'Tangalle', '82200'),
(gen_random_uuid(), 'HAM', 'HAM-BEL', 'Beliatta', '82400'),
(gen_random_uuid(), 'HAM', 'HAM-AMB', 'Ambalantota', '82100'),
(gen_random_uuid(), 'HAM', 'HAM-TIS', 'Tissamaharama', '82600'),
(gen_random_uuid(), 'HAM', 'HAM-KAT', 'Kataragama', '91400'),
(gen_random_uuid(), 'HAM', 'HAM-WAL', 'Walasmulla', '82450'),
(gen_random_uuid(), 'HAM', 'HAM-WEE', 'Weeraketiya', '82240');

-- Seed postal zones for Jaffna
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'JAF', 'JAF-JAF', 'Jaffna Town', '40000'),
(gen_random_uuid(), 'JAF', 'JAF-NAL', 'Nallur', '40000'),
(gen_random_uuid(), 'JAF', 'JAF-CHV', 'Chavakachcheri', '40500'),
(gen_random_uuid(), 'JAF', 'JAF-PTP', 'Point Pedro', '40600'),
(gen_random_uuid(), 'JAF', 'JAF-CHU', 'Chunnakam', '40000'),
(gen_random_uuid(), 'JAF', 'JAF-VVT', 'Valvettithurai', '40630'),
(gen_random_uuid(), 'JAF', 'JAF-KAR', 'Karainagar', '40000'),
(gen_random_uuid(), 'JAF', 'JAF-TEL', 'Tellippalai', '40000'),
(gen_random_uuid(), 'JAF', 'JAF-KOP', 'Kopay', '40000');

-- Seed postal zones for Kilinochchi
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'KIL', 'KIL-KIL', 'Kilinochchi Town', '44000'),
(gen_random_uuid(), 'KIL', 'KIL-PAR', 'Paranthan', '44000'),
(gen_random_uuid(), 'KIL', 'KIL-PAL', 'Pallai', '44000'),
(gen_random_uuid(), 'KIL', 'KIL-POO', 'Poonakary', '44000');

-- Seed postal zones for Mannar
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'MAN', 'MAN-MAN', 'Mannar Town', '41000'),
(gen_random_uuid(), 'MAN', 'MAN-MUR', 'Murunkan', '41000'),
(gen_random_uuid(), 'MAN', 'MAN-PES', 'Pesalai', '41000'),
(gen_random_uuid(), 'MAN', 'MAN-MAD', 'Madhu', '41000'),
(gen_random_uuid(), 'MAN', 'MAN-TAL', 'Talaimannar', '41000');

-- Seed postal zones for Vavuniya
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'VAV', 'VAV-VAV', 'Vavuniya Town', '43000'),
(gen_random_uuid(), 'VAV', 'VAV-CHE', 'Cheddikulam', '43000'),
(gen_random_uuid(), 'VAV', 'VAV-NED', 'Nedunkeni', '43000'),
(gen_random_uuid(), 'VAV', 'VAV-OMA', 'Omanthai', '43000');

-- Seed postal zones for Mullaitivu
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'MUL', 'MUL-MUL', 'Mullaitivu Town', '42000'),
(gen_random_uuid(), 'MUL', 'MUL-PKY', 'Puthukkudiyiruppu', '42000'),
(gen_random_uuid(), 'MUL', 'MUL-ODD', 'Oddusuddan', '42000'),
(gen_random_uuid(), 'MUL', 'MUL-MAN', 'Mankulam', '42000');

-- Seed postal zones for Batticaloa
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'BAT', 'BAT-BAT', 'Batticaloa Town', '30000'),
(gen_random_uuid(), 'BAT', 'BAT-KAT', 'Kattankudy', '30100'),
(gen_random_uuid(), 'BAT', 'BAT-ERA', 'Eravur', '30300'),
(gen_random_uuid(), 'BAT', 'BAT-VAL', 'Valachchenai', '30400'),
(gen_random_uuid(), 'BAT', 'BAT-KAL', 'Kaluwanchikudy', '30200'),
(gen_random_uuid(), 'BAT', 'BAT-CHE', 'Chenkalady', '30350');

-- Seed postal zones for Ampara
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'AMP', 'AMP-AMP', 'Ampara Town', '32000'),
(gen_random_uuid(), 'AMP', 'AMP-KAL', 'Kalmunai', '32300'),
(gen_random_uuid(), 'AMP', 'AMP-SAM', 'Sammanthurai', '32200'),
(gen_random_uuid(), 'AMP', 'AMP-AKK', 'Akkaraipattu', '32400'),
(gen_random_uuid(), 'AMP', 'AMP-POT', 'Pottuvil / Arugam Bay', '32500'),
(gen_random_uuid(), 'AMP', 'AMP-SAI', 'Sainthamaruthu', '32300'),
(gen_random_uuid(), 'AMP', 'AMP-DEH', 'Dehiattakandiya', '32150');

-- Seed postal zones for Trincomalee
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'TRI', 'TRI-TRI', 'Trincomalee Town', '31000'),
(gen_random_uuid(), 'TRI', 'TRI-KIN', 'Kinniya', '31100'),
(gen_random_uuid(), 'TRI', 'TRI-MUT', 'Mutur', '31200'),
(gen_random_uuid(), 'TRI', 'TRI-KAN', 'Kantale', '31300'),
(gen_random_uuid(), 'TRI', 'TRI-NIL', 'Nilaveli', '31010');

-- Seed postal zones for Kurunegala
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'KUR', 'KUR-KUR', 'Kurunegala Town', '60000'),
(gen_random_uuid(), 'KUR', 'KUR-KUL', 'Kuliyapitiya', '60200'),
(gen_random_uuid(), 'KUR', 'KUR-NAR', 'Narammala', '60100'),
(gen_random_uuid(), 'KUR', 'KUR-WAR', 'Wariyapola', '60400'),
(gen_random_uuid(), 'KUR', 'KUR-PAN', 'Pannala', '60160'),
(gen_random_uuid(), 'KUR', 'KUR-POL', 'Polgahawela', '60300'),
(gen_random_uuid(), 'KUR', 'KUR-ALA', 'Alawwa', '60280'),
(gen_random_uuid(), 'KUR', 'KUR-IBB', 'Ibbagamuwa', '60500'),
(gen_random_uuid(), 'KUR', 'KUR-GIR', 'Giriulla', '60140'),
(gen_random_uuid(), 'KUR', 'KUR-MAW', 'Mawathagama', '60060');

-- Seed postal zones for Puttalam
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'PUT', 'PUT-PUT', 'Puttalam Town', '61300'),
(gen_random_uuid(), 'PUT', 'PUT-CHI', 'Chilaw', '61000'),
(gen_random_uuid(), 'PUT', 'PUT-WEN', 'Wennappuwa', '61170'),
(gen_random_uuid(), 'PUT', 'PUT-MAR', 'Marawila', '61160'),
(gen_random_uuid(), 'PUT', 'PUT-DAN', 'Dankotuwa', '61180'),
(gen_random_uuid(), 'PUT', 'PUT-ANA', 'Anamaduwa', '61500'),
(gen_random_uuid(), 'PUT', 'PUT-KAL', 'Kalpitiya', '61280'),
(gen_random_uuid(), 'PUT', 'PUT-NAT', 'Nattandiya', '61140');

-- Seed postal zones for Anuradhapura
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'ANU', 'ANU-ANU', 'Anuradhapura Town', '50000'),
(gen_random_uuid(), 'ANU', 'ANU-KEK', 'Kekirawa', '50100'),
(gen_random_uuid(), 'ANU', 'ANU-MED', 'Medawachchiya', '50500'),
(gen_random_uuid(), 'ANU', 'ANU-TAM', 'Tambuttegama', '50140'),
(gen_random_uuid(), 'ANU', 'ANU-EPP', 'Eppawala', '50180'),
(gen_random_uuid(), 'ANU', 'ANU-MIH', 'Mihintale', '50300'),
(gen_random_uuid(), 'ANU', 'ANU-GAL', 'Galenbindunuwewa', '50340'),
(gen_random_uuid(), 'ANU', 'ANU-NOC', 'Nochchiyagama', '50200'),
(gen_random_uuid(), 'ANU', 'ANU-HAB', 'Habarana', '50150');

-- Seed postal zones for Polonnaruwa
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'POL', 'POL-POL', 'Polonnaruwa Town', '51000'),
(gen_random_uuid(), 'POL', 'POL-KAD', 'Kaduruwela', '51000'),
(gen_random_uuid(), 'POL', 'POL-HIN', 'Hingurakgoda', '51400'),
(gen_random_uuid(), 'POL', 'POL-MED', 'Medirigiriya', '51500'),
(gen_random_uuid(), 'POL', 'POL-WEL', 'Welikanda', '51070'),
(gen_random_uuid(), 'POL', 'POL-MIN', 'Minneriya', '51410');

-- Seed postal zones for Badulla
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'BAD', 'BAD-BAD', 'Badulla Town', '90000'),
(gen_random_uuid(), 'BAD', 'BAD-BAN', 'Bandarawela', '90100'),
(gen_random_uuid(), 'BAD', 'BAD-ELL', 'Ella', '90090'),
(gen_random_uuid(), 'BAD', 'BAD-WEL', 'Welimada', '90200'),
(gen_random_uuid(), 'BAD', 'BAD-MAH', 'Mahiyanganaya', '90700'),
(gen_random_uuid(), 'BAD', 'BAD-HAL', 'Hali-Ela', '90060'),
(gen_random_uuid(), 'BAD', 'BAD-HAP', 'Haputale', '90160'),
(gen_random_uuid(), 'BAD', 'BAD-PAS', 'Passara', '90500'),
(gen_random_uuid(), 'BAD', 'BAD-DIY', 'Diyatalawa', '90150');

-- Seed postal zones for Monaragala
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'MON', 'MON-MON', 'Monaragala Town', '91000'),
(gen_random_uuid(), 'MON', 'MON-WEL', 'Wellawaya', '91200'),
(gen_random_uuid(), 'MON', 'MON-BUT', 'Buttala', '91100'),
(gen_random_uuid(), 'MON', 'MON-BIB', 'Bibile', '91500'),
(gen_random_uuid(), 'MON', 'MON-KAT', 'Kataragama', '91400'),
(gen_random_uuid(), 'MON', 'MON-SIY', 'Siyambalanduwa', '91300');

-- Seed postal zones for Ratnapura
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'RAT', 'RAT-RAT', 'Ratnapura Town', '70000'),
(gen_random_uuid(), 'RAT', 'RAT-EMB', 'Embilipitiya', '70200'),
(gen_random_uuid(), 'RAT', 'RAT-BAL', 'Balangoda', '70100'),
(gen_random_uuid(), 'RAT', 'RAT-PEL', 'Pelmadulla', '70070'),
(gen_random_uuid(), 'RAT', 'RAT-KUR', 'Kuruwita', '70500'),
(gen_random_uuid(), 'RAT', 'RAT-EHE', 'Eheliyagoda', '70600'),
(gen_random_uuid(), 'RAT', 'RAT-KAH', 'Kahawatta', '70150'),
(gen_random_uuid(), 'RAT', 'RAT-KAL', 'Kalawana', '70450');

-- Seed postal zones for Kegalle
INSERT INTO district_postal_zones (id, district_code, code, name, postal_code) VALUES
(gen_random_uuid(), 'KEG', 'KEG-KEG', 'Kegalle Town', '71000'),
(gen_random_uuid(), 'KEG', 'KEG-MAW', 'Mawanella', '71500'),
(gen_random_uuid(), 'KEG', 'KEG-WAR', 'Warakapola', '71600'),
(gen_random_uuid(), 'KEG', 'KEG-RUW', 'Ruwanwella', '71700'),
(gen_random_uuid(), 'KEG', 'KEG-YAT', 'Yatiyantota', '71720'),
(gen_random_uuid(), 'KEG', 'KEG-DEH', 'Dehiowita', '71400'),
(gen_random_uuid(), 'KEG', 'KEG-RAM', 'Rambukkana', '71100'),
(gen_random_uuid(), 'KEG', 'KEG-DER', 'Deraniyagala', '71430');

