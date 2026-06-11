-- The fiscal outbox poll (BridgeService.dispatchPending) runs every ~10s plus on every
-- payment commit and bridge reconnect:
--   WHERE fiscal_status = 'PENDING' AND (fiscal_sent_at IS NULL OR fiscal_sent_at < :cutoff)
--   ORDER BY created_at ASC
-- Without a matching index this is a sequential scan of the whole (ever-growing) payment
-- table. A partial index on the PENDING rows only stays tiny — terminal SUCCESS/FAILED rows
-- are excluded — and covers both the predicate and the ORDER BY.
CREATE INDEX idx_payment_fiscal_pending
    ON payment (created_at)
    WHERE fiscal_status = 'PENDING';
