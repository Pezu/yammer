-- Seed the base set of roles. Idempotent on the unique `role` column.
INSERT INTO role (role) VALUES
    ('WAITER'),
    ('BARMAN'),
    ('ADMIN'),
    ('SERVICE'),
    ('SUPER')
ON CONFLICT (role) DO NOTHING;
