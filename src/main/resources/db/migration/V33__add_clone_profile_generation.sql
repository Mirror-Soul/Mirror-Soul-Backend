ALTER TABLE clones
    ADD COLUMN profile_source_hash VARCHAR(64) NULL;

CREATE TABLE clone_profile_generation_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clone_id BIGINT NOT NULL,
    trigger_type VARCHAR(40) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NULL,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(500) NULL,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_clone_profile_generation_jobs PRIMARY KEY (id),
    CONSTRAINT fk_clone_profile_generation_jobs_clone
        FOREIGN KEY (clone_id) REFERENCES clones(id) ON DELETE CASCADE,
    CONSTRAINT chk_clone_profile_generation_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'STALE')),
    CONSTRAINT chk_clone_profile_generation_jobs_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX idx_clone_profile_jobs_status_next_attempt
    ON clone_profile_generation_jobs (status, next_attempt_at);
CREATE INDEX idx_clone_profile_jobs_clone_status
    ON clone_profile_generation_jobs (clone_id, status);
