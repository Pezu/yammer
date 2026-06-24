-- Online (Netopia) self-service payments for non-pay-later order points.
--
-- A customer at a pay-now order point does NOT get a real order until the gateway confirms payment.
-- The cart is parked in `online_payment` (status PENDING) while the customer is on Netopia; the
-- order + payment are created only when the IPN confirms (status 3/5). This keeps unpaid attempts
-- out of every existing order/bill/report query — no order exists until it is paid.

CREATE TABLE online_payment (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    event_id       UUID REFERENCES event(id) ON DELETE CASCADE,
    amount         NUMERIC(10, 2) NOT NULL,
    -- JSON snapshot of the validated cart: [{menuItemId,name,price,quantity}] — name/price are
    -- snapshotted at start so confirmation does not depend on the menu being unchanged.
    items          TEXT NOT NULL,
    status         VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING / PAID / FAILED / EXPIRED
    ntp_id         VARCHAR(64),                            -- Netopia transaction id
    order_id       UUID REFERENCES orders(id) ON DELETE SET NULL,   -- the order created on confirm
    payment_id     UUID REFERENCES payment(id) ON DELETE SET NULL,  -- the payment created on confirm
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP
);

-- The scheduled sweep expires stale PENDING intents; index the predicate it scans.
CREATE INDEX idx_online_payment_status_created ON online_payment (status, created_at);

-- Gateway transaction id on the settled payment (Netopia ntpID), for reconciliation.
ALTER TABLE payment ADD COLUMN external_ref VARCHAR(64);
