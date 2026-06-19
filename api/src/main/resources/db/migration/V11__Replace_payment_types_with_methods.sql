-- The payment_type catalog (V10) was dropped in favor of a fixed per-order-point selection of the
-- built-in payment methods (CASH/CARD). Replace order_point.payment_type_ids (catalog references)
-- with order_point.payment_methods (PaymentMethod enum names; empty = all), then drop the catalog.
ALTER TABLE order_point DROP COLUMN payment_type_ids;
ALTER TABLE order_point ADD COLUMN payment_methods TEXT[] NOT NULL DEFAULT '{}';
DROP TABLE payment_type;
