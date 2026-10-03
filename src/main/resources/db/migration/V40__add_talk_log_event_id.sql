ALTER TABLE talk_logs
    ADD COLUMN event_id VARCHAR(36) NULL;

ALTER TABLE talk_logs
    ADD CONSTRAINT uk_talk_logs_call_event UNIQUE (video_call_id, event_id);
