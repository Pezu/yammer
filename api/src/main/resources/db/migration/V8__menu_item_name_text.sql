-- Menu item names now hold rich text (HTML), which can exceed 255 chars.
ALTER TABLE menu_item ALTER COLUMN name TYPE TEXT;
