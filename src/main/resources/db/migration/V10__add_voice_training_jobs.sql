ALTER TABLE interview_record
    ADD COLUMN answer_audio_object_key VARCHAR(500) NULL;

CREATE TABLE voice_training_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    sqs_message_id VARCHAR(100) NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_voice_training_jobs PRIMARY KEY (id),
    CONSTRAINT fk_voice_training_jobs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE
);

CREATE TABLE voice_training_job_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    voice_training_job_id BIGINT NOT NULL,
    interview_record_id BIGINT NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,

    CONSTRAINT pk_voice_training_job_files PRIMARY KEY (id),
    CONSTRAINT fk_voice_training_job_files_job
        FOREIGN KEY (voice_training_job_id) REFERENCES voice_training_jobs(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_voice_training_job_files_interview_record
        FOREIGN KEY (interview_record_id) REFERENCES interview_record(id)
            ON DELETE CASCADE
);
