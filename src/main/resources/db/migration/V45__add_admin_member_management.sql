ALTER TABLE users ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN status_before_withdrawal VARCHAR(20) NULL;
ALTER TABLE users ADD COLUMN status_before_suspension VARCHAR(20) NULL;

CREATE TABLE member_action_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason_code VARCHAR(30) NOT NULL,
    before_status VARCHAR(30) NOT NULL,
    after_status VARCHAR(30) NOT NULL,
    expected_version BIGINT NOT NULL,
    result_version BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    acted_at DATETIME NOT NULL,
    CONSTRAINT uk_member_action_key UNIQUE (member_id,idempotency_key),
    CONSTRAINT fk_member_action_member FOREIGN KEY(member_id) REFERENCES users(id),
    CONSTRAINT fk_member_action_actor FOREIGN KEY(actor_id) REFERENCES users(id)
);
CREATE INDEX idx_member_action_member_id ON member_action_history(member_id,id);

CREATE TABLE member_deletion_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    member_uuid VARCHAR(36) NOT NULL,
    backend_state VARCHAR(20) NOT NULL,
    ai_state VARCHAR(20) NOT NULL,
    backend_attempts INT NOT NULL DEFAULT 0,
    ai_attempts INT NOT NULL DEFAULT 0,
    backend_attempt_limit INT NOT NULL DEFAULT 3,
    ai_attempt_limit INT NOT NULL DEFAULT 3,
    backend_error VARCHAR(500) NULL,
    ai_error VARCHAR(500) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    ai_lease_until DATETIME NULL,
    CONSTRAINT uk_member_deletion_member UNIQUE(member_id),
    CONSTRAINT fk_member_deletion_member FOREIGN KEY(member_id) REFERENCES users(id)
);
CREATE INDEX idx_member_deletion_backend ON member_deletion_jobs(backend_state,backend_attempts);
CREATE INDEX idx_member_deletion_ai ON member_deletion_jobs(ai_state,ai_attempts,ai_lease_until);
