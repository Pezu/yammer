-- Fiscal receipt tracking on payments.
ALTER TABLE payment
    ADD COLUMN fiscal_status  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN receipt_number VARCHAR(64);
