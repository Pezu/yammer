-- Order points belong to a location (scoped through the location's client).
-- An order point is a named spot orders are taken at; pay_later marks whether
-- payment can be deferred (open tab) rather than settled immediately.
CREATE TABLE order_point (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    pay_later   BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_order_point_location_id ON order_point(location_id);
