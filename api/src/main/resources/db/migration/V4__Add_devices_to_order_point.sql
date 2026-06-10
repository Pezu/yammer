-- Link an order point to its printer and cash register (integrations at the location).
ALTER TABLE order_point
    ADD COLUMN printer_id       UUID REFERENCES integration(id) ON DELETE SET NULL,
    ADD COLUMN cash_register_id UUID REFERENCES integration(id) ON DELETE SET NULL;
CREATE INDEX idx_order_point_printer ON order_point(printer_id);
CREATE INDEX idx_order_point_cash_register ON order_point(cash_register_id);
