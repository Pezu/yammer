-- A pay-later order point (a table) can point to a non-pay-later order point
-- (a bar / service station) that serves it. Self-referencing FK, nullable.
ALTER TABLE order_point
    ADD COLUMN service_order_point_id UUID REFERENCES order_point(id) ON DELETE SET NULL;

CREATE INDEX idx_order_point_service_id ON order_point(service_order_point_id);
