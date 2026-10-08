CREATE TABLE job_verification_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    submitted_legal_name VARCHAR(100) NOT NULL,
    claimed_job VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_user_id BIGINT NULL,
    rejection_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    reviewed_at DATETIME NULL,

    CONSTRAINT pk_job_verification_requests PRIMARY KEY (id),
    CONSTRAINT fk_job_verification_requests_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_job_verification_requests_reviewer
        FOREIGN KEY (reviewer_user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_job_verification_requests_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_job_verification_requests_review
        CHECK (
            (status = 'PENDING' AND reviewed_at IS NULL AND reviewer_user_id IS NULL AND rejection_reason IS NULL)
            OR (status = 'APPROVED' AND reviewed_at IS NOT NULL AND rejection_reason IS NULL)
            OR (status = 'REJECTED' AND reviewed_at IS NOT NULL AND rejection_reason IS NOT NULL
                AND CHAR_LENGTH(TRIM(rejection_reason)) > 0)
        )
);

CREATE INDEX idx_job_verification_requests_status_created
    ON job_verification_requests (status, created_at, id);

CREATE INDEX idx_job_verification_requests_user_created
    ON job_verification_requests (user_id, created_at, id);

CREATE TABLE job_verification_request_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id BIGINT NOT NULL,
    bucket VARCHAR(255) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    object_version_id VARCHAR(255) NULL,
    object_etag VARCHAR(255) NULL,
    display_order INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_job_verification_request_files PRIMARY KEY (id),
    CONSTRAINT fk_job_verification_request_files_request
        FOREIGN KEY (request_id) REFERENCES job_verification_requests(id) ON DELETE CASCADE,
    CONSTRAINT uk_job_verification_request_files_order UNIQUE (request_id, display_order),
    CONSTRAINT uk_job_verification_request_files_object UNIQUE (request_id, object_key),
    CONSTRAINT chk_job_verification_request_files_order CHECK (display_order >= 0)
);
