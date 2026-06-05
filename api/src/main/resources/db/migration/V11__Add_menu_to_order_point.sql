-- An order point can be tied to a menu (the menu shown when ordering there).
-- Nullable: a point may have no menu. Menus and order points both belong to a
-- location, so a point's menu is expected to be one of its location's menus.
ALTER TABLE order_point
    ADD COLUMN menu_id UUID REFERENCES menu(id) ON DELETE SET NULL;

CREATE INDEX idx_order_point_menu_id ON order_point(menu_id);
