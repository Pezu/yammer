-- Customer directory (backoffice → Configuration → Customers). Global (not tenant-scoped).
CREATE TABLE customer (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    phone      VARCHAR(50),
    email      VARCHAR(255)
);
