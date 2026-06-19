-- Link orders directly to an event (like order points, menus and assignments already are).
-- An order is placed at an order point, which already carries the event, so existing rows
-- are backfilled from their order point.
ALTER TABLE orders
    ADD COLUMN event_id UUID REFERENCES event(id) ON DELETE CASCADE;

UPDATE orders o
   SET event_id = op.event_id
  FROM order_point op
 WHERE op.id = o.order_point_id;

CREATE INDEX idx_orders_event ON orders(event_id);
