-- Payments taken against an order point's running bill. amount = portion of the
-- bill settled; tip is on top. method is CASH / CARD / PROTOCOL.
CREATE TABLE payment (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    amount         NUMERIC(10, 2) NOT NULL,
    tip            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    method         VARCHAR(16) NOT NULL,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_order_point ON payment(order_point_id);
