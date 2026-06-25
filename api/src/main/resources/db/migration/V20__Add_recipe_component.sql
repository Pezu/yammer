-- Recipe (Rețetar) components for a combined product: free-form rows of name/qty/unit/percentage.
CREATE TABLE recipe_component (
    id           UUID PRIMARY KEY,
    menu_item_id UUID NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    name         TEXT NOT NULL DEFAULT '',
    quantity     NUMERIC,
    unit         VARCHAR(32),
    percentage   NUMERIC,
    sort_order   INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_recipe_component_menu_item ON recipe_component (menu_item_id);
