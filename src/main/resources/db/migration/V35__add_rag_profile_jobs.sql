CREATE TABLE rag_profile_jobs (
    clone_id BIGINT NOT NULL PRIMARY KEY,
    requested_revision BIGINT NOT NULL DEFAULT 0,
    delivered_revision BIGINT NOT NULL DEFAULT 0,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    lease_until DATETIME(6) NULL,
    lease_token VARCHAR(36) NULL,
    last_error VARCHAR(100) NULL,
    CONSTRAINT fk_rag_profile_jobs_clone FOREIGN KEY (clone_id) REFERENCES clones(id),
    INDEX idx_rag_profile_jobs_due (next_attempt_at, lease_until)
);
