CREATE TABLE admin_staff (
 user_id BIGINT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, change_sequence BIGINT NOT NULL DEFAULT 0,
 display_name VARCHAR(100) NOT NULL, state VARCHAR(20) NOT NULL,
 invite_hash VARCHAR(64), invite_cipher VARCHAR(512), invite_expires_at DATETIME, updated_at DATETIME NOT NULL,
 CONSTRAINT fk_admin_staff_user FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE TABLE admin_staff_permissions (
 user_id BIGINT NOT NULL, permission VARCHAR(30) NOT NULL,
 PRIMARY KEY(user_id,permission), CONSTRAINT fk_admin_permission_staff FOREIGN KEY(user_id) REFERENCES admin_staff(user_id)
);
CREATE TABLE admin_reauthentications (
 token_hash VARCHAR(64) PRIMARY KEY, user_id BIGINT NOT NULL, session_version BIGINT NOT NULL,
 expires_at DATETIME NOT NULL, used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_admin_reauth_expiry ON admin_reauthentications(expires_at);
CREATE TABLE admin_commands (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL, request_key VARCHAR(100) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, target_id BIGINT NOT NULL,
 CONSTRAINT uk_admin_command UNIQUE(actor_id,request_key)
);
CREATE TABLE admin_audit_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, staff_id BIGINT,
 action VARCHAR(120) NOT NULL, target VARCHAR(120) NOT NULL, occurred_at DATETIME NOT NULL,
 result VARCHAR(20) NOT NULL, reason_code VARCHAR(60) NOT NULL, correlation_id VARCHAR(36) NOT NULL
);
CREATE INDEX idx_admin_audit_staff_time ON admin_audit_events(staff_id,occurred_at,id);
CREATE INDEX idx_admin_audit_time ON admin_audit_events(occurred_at,id);
CREATE INDEX idx_admin_audit_correlation ON admin_audit_events(correlation_id,id);
