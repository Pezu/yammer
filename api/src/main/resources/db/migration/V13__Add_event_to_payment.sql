-- Link payments directly to an event (mirrors orders, order points and menus).
-- A payment is taken at an order point, which already carries the event, so existing
-- rows are backfilled from their order point.
ALTER TABLE payment
    ADD COLUMN event_id UUID REFERENCES event(id) ON DELETE CASCADE;

UPDATE payment p
   SET event_id = op.event_id
  FROM order_point op
 WHERE op.id = p.order_point_id;

CREATE INDEX idx_payment_event ON payment(event_id);
