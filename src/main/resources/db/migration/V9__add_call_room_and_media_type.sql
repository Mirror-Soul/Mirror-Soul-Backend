ALTER TABLE video_calls
    ADD COLUMN room_id VARCHAR(100) NOT NULL,
    ADD COLUMN media_type VARCHAR(20) NOT NULL DEFAULT 'VOICE';

ALTER TABLE video_calls
    ADD CONSTRAINT uk_video_calls_room_id UNIQUE (room_id);