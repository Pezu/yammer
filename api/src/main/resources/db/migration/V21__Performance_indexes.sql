-- Performance indexes.

-- The hot access pattern is "rows for a set of order points, filtered/sorted by time"
-- (service board, waiter orders, payments list, sales report). A composite index on
-- (order_point_id, created_at) satisfies both the order_point_id filter and the
-- created_at range/sort from a single index, and still serves order_point_id-only
-- lookups and FK cascade deletes — so the old single-column indexes are redundant.
CREATE INDEX idx_orders_op_created ON orders (order_point_id, created_at);
CREATE INDEX idx_payment_op_created ON payment (order_point_id, created_at);

DROP INDEX IF EXISTS idx_orders_order_point;
DROP INDEX IF EXISTS idx_payment_order_point;

-- users.client_id (FK added in V4) was never indexed; user listings filter by it.
CREATE INDEX idx_users_client_id ON users (client_id);
