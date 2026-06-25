-- "Combined" flag for menu products: included in the Recipes (Rețetar) aggregation.
ALTER TABLE menu_item ADD COLUMN combined boolean NOT NULL DEFAULT false;
