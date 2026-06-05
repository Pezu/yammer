-- VAT types are global catalog data (not client-scoped), managed by SUPER.
CREATE TABLE vat_type (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(100) NOT NULL,
    value NUMERIC(5, 2) NOT NULL
);

INSERT INTO vat_type (name, value) VALUES
    ('Standard', 19.00),
    ('Reduced', 9.00),
    ('Zero', 0.00);

-- A menu product can reference a VAT type.
ALTER TABLE menu_item ADD COLUMN vat_type_id UUID REFERENCES vat_type(id);
