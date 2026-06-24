-- Attribute self-service (pay-now) orders to a customer, so the customer can see their order
-- history from the QR page. Set on the order created when an online payment is confirmed.
ALTER TABLE orders ADD COLUMN customer_id UUID REFERENCES customer(id) ON DELETE SET NULL;
ALTER TABLE online_payment ADD COLUMN customer_id UUID REFERENCES customer(id) ON DELETE SET NULL;
CREATE INDEX idx_orders_customer ON orders (customer_id);
