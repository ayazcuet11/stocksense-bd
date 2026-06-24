-- Bangladesh festivals — 2026 & 2027 (approximate Hijri-derived dates; accurate calendar in Phase 6).
-- Needed so the Festival Demand Agent has upcoming events to plan against. The previous year's sales
-- (seeded for the prior year) supply the historical surge signal the agent compares against.
INSERT INTO festival_events (name, type, gregorian_date, hijri_date) VALUES
('Mango Season 2026',       'MANGO_SEASON',   '2026-07-05', NULL),
('Hilsa Season 2026',       'HILSA_SEASON',   '2026-08-15', NULL),
('Eid-e-Milad-un-Nabi 2026','EID_MILAD',      '2026-08-26', '1448-03-12'),
('Durga Puja 2026',         'DURGA_PUJA',     '2026-10-19', NULL),
('Victory Day 2026',        'NATIONAL',       '2026-12-16', NULL),
('Ramadan Start 2027',      'RAMADAN',        '2027-02-17', '1448-09-01'),
('Eid ul-Fitr 2027',        'EID_UL_FITR',    '2027-03-19', '1448-10-01'),
('Pohela Boishakh 2027',    'POHELA_BOISHAKH','2027-04-14', NULL),
('Mango Season 2027',       'MANGO_SEASON',   '2027-05-01', NULL),
('Eid ul-Adha 2027',        'EID_UL_ADHA',    '2027-05-26', '1448-12-10');
