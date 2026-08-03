ALTER TABLE talk_logs
    ADD COLUMN edited BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN edited_at DATETIME NULL;

DROP TABLE talk_log_corrections;
