ALTER TABLE voice_training_jobs
    ADD COLUMN error_code VARCHAR(100) NULL;

ALTER TABLE voice_training_jobs
    ADD COLUMN error_retryable BOOLEAN NULL;

ALTER TABLE ai_voice_profiles
    ADD CONSTRAINT uk_ai_voice_profiles_training_job UNIQUE (voice_training_job_id);
