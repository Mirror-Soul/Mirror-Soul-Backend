CREATE TABLE talk_log_corrections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    talk_log_id BIGINT NOT NULL,
    corrected_by_user_id BIGINT NOT NULL,
    original_message TEXT NOT NULL,
    corrected_message TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_talk_log_corrections PRIMARY KEY (id),
    CONSTRAINT uk_talk_log_corrections_talk_log UNIQUE (talk_log_id),
    CONSTRAINT fk_talk_log_corrections_talk_log
        FOREIGN KEY (talk_log_id) REFERENCES talk_logs(id) ON DELETE CASCADE,
    CONSTRAINT fk_talk_log_corrections_user
        FOREIGN KEY (corrected_by_user_id) REFERENCES users(id) ON DELETE CASCADE
);
