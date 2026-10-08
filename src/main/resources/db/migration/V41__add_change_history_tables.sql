CREATE TABLE talk_time_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    video_call_id BIGINT NULL,
    reason VARCHAR(30) NOT NULL,
    delta_seconds INT NOT NULL,
    balance_after_seconds INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_talk_time_transactions PRIMARY KEY (id),
    CONSTRAINT fk_talk_time_transactions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_talk_time_transactions_call
        FOREIGN KEY (video_call_id) REFERENCES video_calls(id) ON DELETE SET NULL,
    CONSTRAINT uk_talk_time_transactions_call UNIQUE (video_call_id)
);

CREATE INDEX idx_talk_time_transactions_user_created
    ON talk_time_transactions (user_id, created_at, id);

-- Previous balance changes cannot be reconstructed; preserve each current balance as the starting point.
INSERT INTO talk_time_transactions (user_id, reason, delta_seconds, balance_after_seconds)
SELECT id, 'OPENING_BALANCE', remaining_talk_time, remaining_talk_time FROM users;

ALTER TABLE talk_logs
    ADD COLUMN revision_number INT NOT NULL DEFAULT 0;

CREATE TABLE talk_log_revisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    talk_log_id BIGINT NOT NULL,
    editor_user_id BIGINT NULL,
    revision_number INT NOT NULL,
    previous_message TEXT NOT NULL,
    new_message TEXT NOT NULL,
    edited_at DATETIME NOT NULL,

    CONSTRAINT pk_talk_log_revisions PRIMARY KEY (id),
    CONSTRAINT fk_talk_log_revisions_talk_log
        FOREIGN KEY (talk_log_id) REFERENCES talk_logs(id) ON DELETE CASCADE,
    CONSTRAINT fk_talk_log_revisions_editor
        FOREIGN KEY (editor_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uk_talk_log_revisions_number UNIQUE (talk_log_id, revision_number)
);

CREATE TABLE clone_profile_versions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clone_id BIGINT NOT NULL,
    generation_job_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(50) NOT NULL,
    summary TEXT NOT NULL,
    personality_tags JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_clone_profile_versions PRIMARY KEY (id),
    CONSTRAINT fk_clone_profile_versions_clone
        FOREIGN KEY (clone_id) REFERENCES clones(id) ON DELETE CASCADE,
    CONSTRAINT fk_clone_profile_versions_job
        FOREIGN KEY (generation_job_id) REFERENCES clone_profile_generation_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_clone_profile_versions_job UNIQUE (generation_job_id),
    CONSTRAINT uk_clone_profile_versions_number UNIQUE (clone_id, version_number)
);

CREATE INDEX idx_clone_profile_versions_clone_created
    ON clone_profile_versions (clone_id, created_at, id);
