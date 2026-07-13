CREATE TABLE ai_voice_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clone_id BIGINT NOT NULL,
    voice_training_job_id BIGINT NOT NULL,
    elevenlabs_voice_id VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_ai_voice_profiles PRIMARY KEY (id),
    CONSTRAINT fk_ai_voice_profiles_clone
        FOREIGN KEY (clone_id) REFERENCES clones(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_ai_voice_profiles_voice_training_job
        FOREIGN KEY (voice_training_job_id) REFERENCES voice_training_jobs(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_ai_voice_profiles_clone_active
    ON ai_voice_profiles (clone_id, is_active);
