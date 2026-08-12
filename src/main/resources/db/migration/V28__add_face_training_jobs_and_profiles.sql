CREATE TABLE face_training_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(30) NOT NULL,
    sqs_message_id VARCHAR(100) NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_face_training_jobs PRIMARY KEY (id),
    CONSTRAINT fk_face_training_jobs_user
        FOREIGN KEY (user_id) REFERENCES users(id)
            ON DELETE CASCADE
);

CREATE TABLE face_training_job_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    face_training_job_id BIGINT NOT NULL,
    face_file_id BIGINT NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_face_training_job_files PRIMARY KEY (id),
    CONSTRAINT uk_face_training_job_files_job_object UNIQUE (face_training_job_id, object_key),
    CONSTRAINT fk_face_training_job_files_job
        FOREIGN KEY (face_training_job_id) REFERENCES face_training_jobs(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_face_training_job_files_face_file
        FOREIGN KEY (face_file_id) REFERENCES face_files(id)
);

CREATE TABLE ai_face_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clone_id BIGINT NOT NULL,
    face_training_job_id BIGINT NOT NULL,
    avatar_cache_object_key VARCHAR(500) NULL,
    preview_image_object_key VARCHAR(500) NULL,
    preview_video_object_key VARCHAR(500) NULL,
    quality_score DOUBLE NULL,
    face_similarity_score DOUBLE NULL,
    status VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_ai_face_profiles PRIMARY KEY (id),
    CONSTRAINT uk_ai_face_profiles_training_job UNIQUE (face_training_job_id),
    CONSTRAINT fk_ai_face_profiles_clone
        FOREIGN KEY (clone_id) REFERENCES clones(id)
            ON DELETE CASCADE,
    CONSTRAINT fk_ai_face_profiles_face_training_job
        FOREIGN KEY (face_training_job_id) REFERENCES face_training_jobs(id)
            ON DELETE CASCADE
);

CREATE INDEX idx_face_training_jobs_user_status
    ON face_training_jobs (user_id, status);

CREATE INDEX idx_ai_face_profiles_clone_active
    ON ai_face_profiles (clone_id, is_active);
