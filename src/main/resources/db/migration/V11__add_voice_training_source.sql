ALTER TABLE voice_training_jobs
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'ONBOARDING_INTERVIEW';

ALTER TABLE voice_training_job_files
    MODIFY COLUMN interview_record_id BIGINT NULL;
