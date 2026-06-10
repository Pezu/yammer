-- Scope menus to an event (like order points and assignments).
ALTER TABLE menu
    ADD COLUMN event_id UUID REFERENCES event(id) ON DELETE CASCADE;
CREATE INDEX idx_menu_event ON menu(event_id);
