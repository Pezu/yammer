-- Payment types (backoffice → Catalog → Payment Types). Global catalog, like roles/VAT.
CREATE TABLE payment_type (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE
);

-- Payment types accepted at an order point (references payment_type.id). Multi-valued, stored
-- as a UUID array to match the codebase's array convention (cf. users.roles).
ALTER TABLE order_point
    ADD COLUMN payment_type_ids UUID[] NOT NULL DEFAULT '{}';
