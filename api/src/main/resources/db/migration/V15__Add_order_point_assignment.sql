-- Assign users to an order-point "parent" (the name before the dot: B1, M80).
-- A parent groups its split children (M80.1, M80.2, …); bar points (B1) are their own parent.
CREATE TABLE order_point_assignment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    parent_name VARCHAR(255) NOT NULL,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE (location_id, parent_name, user_id)
);

CREATE INDEX idx_opa_location ON order_point_assignment(location_id);
CREATE INDEX idx_opa_user ON order_point_assignment(user_id);
