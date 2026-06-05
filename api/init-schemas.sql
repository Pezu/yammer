-- Runs once on first container start (docker-entrypoint-initdb.d).
-- Flyway then manages all objects inside this schema.
CREATE SCHEMA IF NOT EXISTS yammer;
