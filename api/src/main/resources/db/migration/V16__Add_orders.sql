-- A placed order at an order point, with its line items (name/price snapshotted
-- from the menu so later menu edits don't change past orders).
CREATE TABLE orders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         VARCHAR(32) NOT NULL DEFAULT 'PLACED'
);
CREATE INDEX idx_orders_order_point ON orders(order_point_id);

CREATE TABLE order_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id UUID,
    name         TEXT NOT NULL,
    price        NUMERIC(10, 2),
    quantity     INTEGER NOT NULL
);
CREATE INDEX idx_order_item_order ON order_item(order_id);
