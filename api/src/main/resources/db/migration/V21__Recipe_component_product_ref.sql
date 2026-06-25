-- A recipe component now references a (non-combined) product instead of a free-form name.
ALTER TABLE recipe_component
    ADD COLUMN component_item_id UUID REFERENCES menu_item(id) ON DELETE SET NULL;

-- name kept as a display snapshot of the referenced product; no longer required.
ALTER TABLE recipe_component ALTER COLUMN name DROP NOT NULL;
ALTER TABLE recipe_component ALTER COLUMN name DROP DEFAULT;
