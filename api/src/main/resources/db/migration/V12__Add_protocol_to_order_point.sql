-- "Protocol" order points (e.g. house/comp tabs) are flagged for separate handling.
ALTER TABLE order_point
    ADD COLUMN protocol BOOLEAN NOT NULL DEFAULT false;
