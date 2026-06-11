-- The waiter/service boards query orders by order point filtered to the active statuses
-- (findByOrderPointIdInAndStatusInOrderByCreatedAtDesc — serviceBoard / myOrders), endpoints the
-- kitchen and waiter UIs poll constantly. idx_orders_op_created (order_point_id, created_at) forces
-- Postgres to scan every historical row per point and discard terminal DELIVERED/CANCELED orders,
-- which dominate as an event progresses. A partial index on just the active rows stays tiny and
-- covers both the predicate and the ORDER BY (mirrors V7's partial PENDING index for payments).
CREATE INDEX idx_orders_op_board
    ON orders (order_point_id, created_at DESC)
    WHERE status IN ('ORDERED', 'READY');
