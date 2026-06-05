-- Seed the default admin user.
-- Password "Cinci_55!!" stored as MD5 (md5('Cinci_55!!') = 62680778b2f7ffb3dcdf0c656ec3c9a6).
INSERT INTO users (username, password, roles)
VALUES ('admin', md5('Cinci_55!!'), ARRAY['ADMIN'])
ON CONFLICT (username) DO NOTHING;
