-- Links an order line to the payment that settled it. NULL = unpaid.
-- A line is paid iff payment_id IS NOT NULL.
ALTER TABLE order_item
    ADD COLUMN payment_id UUID REFERENCES payment(id) ON DELETE SET NULL;

CREATE INDEX idx_order_item_payment ON order_item(payment_id);
