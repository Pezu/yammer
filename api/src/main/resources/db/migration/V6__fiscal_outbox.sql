-- Track when a fiscal RECEIPT was last pushed to the bridge, so the dispatcher can
-- re-send PENDING payments that were never acknowledged (bridge offline / reconnecting)
-- without hammering the device. The `payment` row itself is the fiscal outbox:
--   PENDING (needs sending) -> SUCCESS / FAILED (terminal, set from RECEIPT_RESULT).
ALTER TABLE payment ADD COLUMN fiscal_sent_at TIMESTAMP;
