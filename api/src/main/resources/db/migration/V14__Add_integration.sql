-- Integrations are devices at a location: cash registers and printers.
-- One table, discriminated by `type`.
CREATE TABLE integration (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    ip          VARCHAR(255),
    type        VARCHAR(32) NOT NULL
);

CREATE INDEX idx_integration_location_id ON integration(location_id);
CREATE INDEX idx_integration_location_type ON integration(location_id, type);
