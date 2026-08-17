CREATE TABLE user_blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_blocks PRIMARY KEY (id),
    CONSTRAINT uk_user_blocks_blocker_blocked
        UNIQUE (blocker_user_id, blocked_user_id),
    CONSTRAINT fk_user_blocks_blocker
        FOREIGN KEY (blocker_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_blocks_blocked
        FOREIGN KEY (blocked_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_blocks_different_users
        CHECK (blocker_user_id <> blocked_user_id)
);

CREATE INDEX idx_user_blocks_blocked_user
    ON user_blocks (blocked_user_id);
