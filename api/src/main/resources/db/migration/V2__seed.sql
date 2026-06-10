-- =============================================================================
-- Yammer seed data — base roles, the default admin, and the VAT catalog.
-- =============================================================================

-- Base set of roles.
INSERT INTO role (role) VALUES
    ('WAITER'),
    ('BARMAN'),
    ('ADMIN'),
    ('SERVICE'),
    ('SUPER'),
    ('WATCHER')
ON CONFLICT (role) DO NOTHING;

-- Default admin (SUPER, so it can manage clients).
-- Password "Cinci_55!!" stored as MD5 (md5('Cinci_55!!') = 62680778b2f7ffb3dcdf0c656ec3c9a6).
INSERT INTO users (username, password, roles)
VALUES ('admin', md5('Cinci_55!!'), ARRAY['SUPER'])
ON CONFLICT (username) DO NOTHING;

-- VAT catalog (percentage values).
INSERT INTO vat_type (value) VALUES
    (21.00),
    (11.00),
    (0.00);
