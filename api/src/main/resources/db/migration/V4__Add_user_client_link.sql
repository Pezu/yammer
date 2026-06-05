-- Link users to a client (SUPER users have none).
ALTER TABLE users ADD COLUMN client_id UUID REFERENCES client(id);

-- Promote the seeded admin to SUPER so there is an account able to manage clients.
UPDATE users SET roles = ARRAY['SUPER'] WHERE username = 'admin';
