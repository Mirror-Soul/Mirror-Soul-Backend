CREATE TABLE push_devices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    installation_id CHAR(36) NOT NULL,
    push_token VARCHAR(512) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_push_devices PRIMARY KEY (id),
    CONSTRAINT uk_push_devices_installation UNIQUE (installation_id),
    CONSTRAINT uk_push_devices_token UNIQUE (push_token),
    CONSTRAINT fk_push_devices_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_push_devices_platform
        CHECK (platform IN ('IOS', 'ANDROID'))
);

CREATE INDEX idx_push_devices_user_enabled
    ON push_devices (user_id, enabled);
