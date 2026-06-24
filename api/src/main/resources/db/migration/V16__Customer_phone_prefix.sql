-- Split the international dial prefix from the phone number on customers. Going forward `phone`
-- holds the number only and `prefix` the dial code (e.g. +40); the self-service lookup keys on both.
ALTER TABLE customer ADD COLUMN prefix VARCHAR(8);
