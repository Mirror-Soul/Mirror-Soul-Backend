ALTER TABLE users
    ADD COLUMN last_active_at DATETIME NULL;

CREATE TABLE call_match_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    video_call_id BIGINT NOT NULL,
    twin_similarity INT NULL,
    conversation_summary TEXT NULL,
    summary_points JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    model_version VARCHAR(100) NULL,
    prompt_version VARCHAR(100) NULL,
    failure_reason TEXT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    requested_at DATETIME NULL,
    analyzed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_call_match_analyses PRIMARY KEY (id),
    CONSTRAINT uk_call_match_analyses_video_call UNIQUE (video_call_id),
    CONSTRAINT fk_call_match_analyses_video_call
        FOREIGN KEY (video_call_id) REFERENCES video_calls(id) ON DELETE CASCADE,
    CONSTRAINT chk_call_match_analyses_similarity
        CHECK (twin_similarity IS NULL OR twin_similarity BETWEEN 0 AND 100)
);

CREATE TABLE meeting_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender_user_id BIGINT NOT NULL,
    receiver_user_id BIGINT NOT NULL,
    video_call_id BIGINT NOT NULL,
    message VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    active_pair_key VARCHAR(50) NULL,
    responded_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_meeting_requests PRIMARY KEY (id),
    CONSTRAINT uk_meeting_requests_active_pair UNIQUE (active_pair_key),
    CONSTRAINT fk_meeting_requests_sender
        FOREIGN KEY (sender_user_id) REFERENCES users(id),
    CONSTRAINT fk_meeting_requests_receiver
        FOREIGN KEY (receiver_user_id) REFERENCES users(id),
    CONSTRAINT fk_meeting_requests_video_call
        FOREIGN KEY (video_call_id) REFERENCES video_calls(id),
    CONSTRAINT chk_meeting_requests_different_users
        CHECK (sender_user_id <> receiver_user_id)
);

CREATE INDEX idx_meeting_requests_receiver_status_created
    ON meeting_requests (receiver_user_id, status, created_at);

CREATE INDEX idx_meeting_requests_sender_receiver_status
    ON meeting_requests (sender_user_id, receiver_user_id, status);

CREATE TABLE chat_rooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    participant_pair_key VARCHAR(50) NOT NULL,
    room_type VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    created_from_meeting_request_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_chat_rooms PRIMARY KEY (id),
    CONSTRAINT uk_chat_rooms_participant_pair UNIQUE (participant_pair_key),
    CONSTRAINT uk_chat_rooms_meeting_request UNIQUE (created_from_meeting_request_id),
    CONSTRAINT fk_chat_rooms_meeting_request
        FOREIGN KEY (created_from_meeting_request_id) REFERENCES meeting_requests(id)
);

CREATE TABLE chat_room_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chat_room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME NOT NULL,
    left_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_chat_room_members PRIMARY KEY (id),
    CONSTRAINT uk_chat_room_members_room_user UNIQUE (chat_room_id, user_id),
    CONSTRAINT fk_chat_room_members_room
        FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_room_members_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_chat_room_members_user
    ON chat_room_members (user_id);
