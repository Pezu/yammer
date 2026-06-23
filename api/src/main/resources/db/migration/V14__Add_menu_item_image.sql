-- Optional image (object-storage key) for a menu item — category or product.
ALTER TABLE menu_item
    ADD COLUMN image_object VARCHAR(255);
