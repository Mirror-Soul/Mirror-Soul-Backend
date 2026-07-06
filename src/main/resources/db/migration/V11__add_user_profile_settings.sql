ALTER TABLE users
    ADD COLUMN remaining_talk_time INT NOT NULL DEFAULT 1800,
    ADD COLUMN opponent_voice_volume INT NOT NULL DEFAULT 50,
    ADD COLUMN opponent_speech_speed VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN missed_call_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN low_time_notification_enabled BOOLEAN NOT NULL DEFAULT TRUE;
