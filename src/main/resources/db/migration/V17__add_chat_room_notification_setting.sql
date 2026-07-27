ALTER TABLE chat_room_members
    ADD COLUMN notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;
