-- Order status vocabulary: ORDERED (created), READY, DELIVERED, CANCELED.
-- Rename the legacy 'PLACED' status to 'ORDERED'.
UPDATE orders SET status = 'ORDERED' WHERE status = 'PLACED';

ALTER TABLE orders ALTER COLUMN status SET DEFAULT 'ORDERED';
