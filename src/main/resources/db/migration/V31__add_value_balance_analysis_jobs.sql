CREATE TABLE value_balance_analysis_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    set_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    personality_summary TEXT NULL,
    sqs_message_id VARCHAR(100) NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_value_balance_analysis_jobs PRIMARY KEY (id),
    CONSTRAINT fk_value_balance_analysis_jobs_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_value_balance_analysis_jobs_user_set UNIQUE (user_id, set_number),
    CONSTRAINT chk_value_balance_analysis_jobs_set_number CHECK (set_number BETWEEN 1 AND 13),
    CONSTRAINT chk_value_balance_analysis_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_value_balance_analysis_jobs_user_created
    ON value_balance_analysis_jobs (user_id, created_at);
