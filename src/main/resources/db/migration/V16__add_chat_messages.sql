CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    client_message_id CHAR(36) NOT NULL,
    message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_chat_messages PRIMARY KEY (id),
    CONSTRAINT uk_chat_messages_room_sender_client
        UNIQUE (chat_room_id, sender_user_id, client_message_id),
    CONSTRAINT fk_chat_messages_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_user_id) REFERENCES users(id)
);

CREATE INDEX idx_chat_messages_room_id
    ON chat_messages (chat_room_id, id);

ALTER TABLE chat_room_members
    ADD COLUMN last_read_message_id BIGINT NULL;

ALTER TABLE chat_room_members
    ADD COLUMN last_read_at DATETIME NULL;

ALTER TABLE chat_rooms
    ADD COLUMN last_message_id BIGINT NULL;

ALTER TABLE chat_rooms
    ADD COLUMN last_message_at DATETIME NULL;
