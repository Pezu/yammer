-- =============================================
-- Role
-- =============================================
CREATE TABLE role (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(100) NOT NULL UNIQUE
);

-- =============================================
-- Client
-- =============================================
CREATE TABLE client (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255)
);

-- =============================================
-- Users  (named "users" — "user" is a reserved word in Postgres)
-- =============================================
CREATE TABLE users (
    id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone    VARCHAR(50),
    email    VARCHAR(255),
    roles    TEXT[] NOT NULL DEFAULT '{}'
);
