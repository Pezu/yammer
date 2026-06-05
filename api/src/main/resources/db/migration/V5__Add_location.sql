-- Locations belong to a client (same tenant model as users).
CREATE TABLE location (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name      VARCHAR(255) NOT NULL,
    client_id UUID NOT NULL REFERENCES client(id)
);

CREATE INDEX idx_location_client_id ON location(client_id);
