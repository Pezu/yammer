-- =============================================================================
-- Yammer schema — consolidated initial migration.
-- Tables are created in dependency order (referenced tables first).
-- =============================================================================

-- =============================================
-- Role
-- =============================================
CREATE TABLE role (
    id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(100) NOT NULL UNIQUE
);

-- =============================================
-- Client
-- =============================================
CREATE TABLE client (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name  VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255)
);

-- =============================================
-- Users  (named "users" — "user" is a reserved word in Postgres)
-- SUPER users have no client; everyone else is scoped to one.
-- =============================================
CREATE TABLE users (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username  VARCHAR(100) NOT NULL UNIQUE,
    password  VARCHAR(255) NOT NULL,
    phone     VARCHAR(50),
    email     VARCHAR(255),
    roles     TEXT[] NOT NULL DEFAULT '{}',
    client_id UUID REFERENCES client(id)
);
CREATE INDEX idx_users_client_id ON users(client_id);

-- =============================================
-- Location  (belongs to a client)
-- =============================================
CREATE TABLE location (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name      VARCHAR(255) NOT NULL,
    client_id UUID NOT NULL REFERENCES client(id)
);
CREATE INDEX idx_location_client_id ON location(client_id);

-- =============================================
-- Menu  (a location can have several menus, each a tree of items)
-- =============================================
CREATE TABLE menu (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_menu_location_id ON menu(location_id);

-- =============================================
-- VAT type  (global catalog data, identified by its percentage value)
-- =============================================
CREATE TABLE vat_type (
    id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    value NUMERIC(5, 2) NOT NULL
);

-- =============================================
-- Menu item  (categories: orderable=false; products: orderable=true with a price)
-- name holds rich text (HTML), so it is TEXT.
-- =============================================
CREATE TABLE menu_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menu_id     UUID NOT NULL REFERENCES menu(id) ON DELETE CASCADE,
    parent_id   UUID REFERENCES menu_item(id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    orderable   BOOLEAN NOT NULL DEFAULT false,
    price       NUMERIC(10, 2),
    sort_order  INTEGER NOT NULL DEFAULT 0,
    vat_type_id UUID REFERENCES vat_type(id)
);
CREATE INDEX idx_menu_item_menu_id ON menu_item(menu_id);
CREATE INDEX idx_menu_item_parent_id ON menu_item(parent_id);

-- =============================================
-- Event  (a dated occasion at a location; order points & assignments scope to it)
-- =============================================
CREATE TABLE event (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_id   UUID NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL
);
CREATE INDEX idx_event_location_id ON event(location_id);
CREATE INDEX idx_event_client_id ON event(client_id);

-- =============================================
-- Order point  (a named spot orders are taken at)
--   pay_later              — payment can be deferred (open tab)
--   protocol               — house/comp tab, flagged for separate handling
--   menu_id                — the menu shown when ordering there
--   service_order_point_id — for a pay-later point: the bar/station serving it
--   event_id               — the event this point belongs to
-- =============================================
CREATE TABLE order_point (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id            UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    event_id               UUID REFERENCES event(id) ON DELETE CASCADE,
    name                   VARCHAR(255) NOT NULL,
    pay_later              BOOLEAN NOT NULL DEFAULT false,
    protocol               BOOLEAN NOT NULL DEFAULT false,
    menu_id                UUID REFERENCES menu(id) ON DELETE SET NULL,
    service_order_point_id UUID REFERENCES order_point(id) ON DELETE SET NULL
);
CREATE INDEX idx_order_point_location_id ON order_point(location_id);
CREATE INDEX idx_order_point_event ON order_point(event_id);
CREATE INDEX idx_order_point_menu_id ON order_point(menu_id);
CREATE INDEX idx_order_point_service_id ON order_point(service_order_point_id);

-- =============================================
-- Integration  (devices at a location: cash registers and printers)
-- =============================================
CREATE TABLE integration (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    ip          VARCHAR(255),
    type        VARCHAR(32) NOT NULL
);
CREATE INDEX idx_integration_location_id ON integration(location_id);
CREATE INDEX idx_integration_location_type ON integration(location_id, type);

-- =============================================
-- Order point assignment  (users assigned to an order-point "parent" per event)
-- A parent is the name before the dot (B1, M80); split children (M80.1, …) share it.
-- =============================================
CREATE TABLE order_point_assignment (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    location_id UUID NOT NULL REFERENCES location(id) ON DELETE CASCADE,
    event_id    UUID REFERENCES event(id) ON DELETE CASCADE,
    parent_name VARCHAR(255) NOT NULL,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT order_point_assignment_unique UNIQUE (location_id, event_id, parent_name, user_id)
);
CREATE INDEX idx_opa_location ON order_point_assignment(location_id);
CREATE INDEX idx_opa_user ON order_point_assignment(user_id);
CREATE INDEX idx_opa_event ON order_point_assignment(event_id);

-- =============================================
-- Orders  (a placed order at an order point)
--   status   — ORDERED (created), READY, DELIVERED, CANCELED
--   order_no — sequential, human-friendly number (max(order_no)+1 per client)
-- =============================================
CREATE TABLE orders (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status         VARCHAR(32) NOT NULL DEFAULT 'ORDERED',
    order_no       BIGINT NOT NULL
);
CREATE INDEX idx_orders_op_created ON orders(order_point_id, created_at);
CREATE INDEX idx_orders_order_no ON orders(order_no);

-- =============================================
-- Payment  (taken against an order point's running bill)
--   amount        — portion of the bill settled (tip is on top)
--   method        — CASH / CARD / PROTOCOL
--   fiscal_status — PENDING / … receipt tracking
-- =============================================
CREATE TABLE payment (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_point_id UUID NOT NULL REFERENCES order_point(id) ON DELETE CASCADE,
    amount         NUMERIC(10, 2) NOT NULL,
    tip            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    method         VARCHAR(16) NOT NULL,
    created_by     VARCHAR(100),
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fiscal_status  VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    receipt_number VARCHAR(64)
);
CREATE INDEX idx_payment_op_created ON payment(order_point_id, created_at);

-- =============================================
-- Order item  (line items; name/price snapshotted from the menu at order time)
-- A line is paid iff payment_id IS NOT NULL.
-- =============================================
CREATE TABLE order_item (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id UUID,
    name         TEXT NOT NULL,
    price        NUMERIC(10, 2),
    quantity     INTEGER NOT NULL,
    payment_id   UUID REFERENCES payment(id) ON DELETE SET NULL
);
CREATE INDEX idx_order_item_order ON order_item(order_id);
CREATE INDEX idx_order_item_payment ON order_item(payment_id);
