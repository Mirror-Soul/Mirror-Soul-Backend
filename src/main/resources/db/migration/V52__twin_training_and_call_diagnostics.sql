ALTER TABLE clones ADD COLUMN clone_version BIGINT NOT NULL DEFAULT 0;
-- Version 0 identifies historical results; it does not invent a past deployment sequence.
ALTER TABLE video_calls ADD COLUMN correlation_id VARCHAR(36) NULL;
ALTER TABLE video_calls ADD COLUMN clone_version BIGINT NULL;
ALTER TABLE video_calls ADD COLUMN voice_training_job_id BIGINT NULL;
ALTER TABLE video_calls ADD COLUMN face_training_job_id BIGINT NULL;
ALTER TABLE video_calls ADD COLUMN personality_revision BIGINT NULL;
UPDATE video_calls SET clone_version=0 WHERE clone_version IS NULL;

ALTER TABLE voice_training_jobs ADD COLUMN base_clone_version BIGINT NULL;
ALTER TABLE voice_training_jobs ADD COLUMN result_clone_version BIGINT NULL;
ALTER TABLE voice_training_jobs ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE voice_training_jobs ADD COLUMN retried_from_job_id BIGINT NULL;
ALTER TABLE face_training_jobs ADD COLUMN base_clone_version BIGINT NULL;
ALTER TABLE face_training_jobs ADD COLUMN result_clone_version BIGINT NULL;
ALTER TABLE face_training_jobs ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE face_training_jobs ADD COLUMN retried_from_job_id BIGINT NULL;
ALTER TABLE clone_profile_generation_jobs ADD COLUMN base_clone_version BIGINT NULL;
ALTER TABLE clone_profile_generation_jobs ADD COLUMN result_clone_version BIGINT NULL;
ALTER TABLE clone_profile_generation_jobs ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE clone_profile_generation_jobs ADD COLUMN retried_from_job_id BIGINT NULL;
ALTER TABLE clone_profile_generation_jobs ADD COLUMN retry_allowed BOOLEAN NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN created_at DATETIME(3) NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN started_at DATETIME(3) NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN finished_at DATETIME(3) NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN updated_at DATETIME(3) NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN base_clone_version BIGINT NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE rag_profile_jobs ADD COLUMN training_status VARCHAR(20) NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN training_failure_code VARCHAR(80) NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN training_retry_allowed BOOLEAN NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN training_finished_at DATETIME(3) NULL;
ALTER TABLE rag_profile_jobs ADD COLUMN result_clone_version BIGINT NULL;
UPDATE rag_profile_jobs SET created_at=next_attempt_at,updated_at=next_attempt_at;

CREATE TABLE training_lifecycle_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(64) NOT NULL,
    training_job_id VARCHAR(80) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    event_sequence BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    stage VARCHAR(20) NOT NULL,
    accepted BOOLEAN NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_training_lifecycle_event UNIQUE(event_key)
);
CREATE INDEX idx_training_lifecycle_job ON training_lifecycle_events(training_job_id,accepted,event_sequence);
CREATE TABLE training_retry_commands (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operation_key VARCHAR(64) NOT NULL,
    source_job_id VARCHAR(80) NOT NULL,
    new_job_id VARCHAR(80) NOT NULL,
    actor_id BIGINT NOT NULL,
    retry_count INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_training_retry_operation UNIQUE(operation_key),
    CONSTRAINT uk_training_retry_source UNIQUE(source_job_id),
    CONSTRAINT fk_training_retry_actor FOREIGN KEY(actor_id) REFERENCES users(id)
);
CREATE TABLE call_quality_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    call_id BIGINT NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    turn_key VARCHAR(64) NOT NULL,
    step VARCHAR(30) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    connection_quality VARCHAR(20) NULL,
    packet_loss_percent DOUBLE NULL,
    jitter_ms DOUBLE NULL,
    round_trip_ms DOUBLE NULL,
    bitrate_kbps DOUBLE NULL,
    error_code VARCHAR(30) NULL,
    CONSTRAINT uk_call_quality_event UNIQUE(event_key),
    CONSTRAINT fk_call_quality_call FOREIGN KEY(call_id) REFERENCES video_calls(id)
);
CREATE INDEX idx_call_quality_turn ON call_quality_events(call_id,turn_key,id);
CREATE INDEX idx_call_quality_time ON call_quality_events(occurred_at,call_id);
CREATE TABLE call_stage_measurements (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    call_id BIGINT NOT NULL,
    turn_key VARCHAR(64) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    latency_ms BIGINT NOT NULL,
    measured_at DATETIME(3) NOT NULL,
    CONSTRAINT uk_call_stage_sample UNIQUE(call_id,turn_key,stage),
    CONSTRAINT fk_call_stage_call FOREIGN KEY(call_id) REFERENCES video_calls(id),
    CONSTRAINT ck_call_stage_latency CHECK(latency_ms>=0)
);
CREATE INDEX idx_call_stage_period ON call_stage_measurements(stage,measured_at,latency_ms);
