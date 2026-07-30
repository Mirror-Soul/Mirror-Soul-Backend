CREATE TABLE clone_personality_tags (
    id BIGINT NOT NULL AUTO_INCREMENT,
    clone_id BIGINT NOT NULL,
    content VARCHAR(20) NOT NULL,
    display_order TINYINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_clone_personality_tags PRIMARY KEY (id),
    CONSTRAINT fk_clone_personality_tags_clone
        FOREIGN KEY (clone_id) REFERENCES clones(id)
            ON DELETE CASCADE,
    CONSTRAINT uk_clone_personality_tags_clone_content
        UNIQUE (clone_id, content),
    CONSTRAINT uk_clone_personality_tags_clone_order
        UNIQUE (clone_id, display_order),
    CONSTRAINT chk_clone_personality_tags_display_order
        CHECK (display_order BETWEEN 0 AND 4),
    CONSTRAINT chk_clone_personality_tags_content_not_blank
        CHECK (
            CHAR_LENGTH(TRIM(content)) > 0
            AND content NOT LIKE '%#%'
        )
);

ALTER TABLE ai_voice_profiles
    ADD COLUMN intro_audio_bucket VARCHAR(100) NULL,
    ADD COLUMN intro_audio_object_key VARCHAR(500) NULL,
    ADD COLUMN intro_audio_content_type VARCHAR(100) NULL,
    ADD COLUMN intro_audio_size_bytes BIGINT NULL,
    ADD COLUMN intro_audio_duration_ms INT NULL,
    ADD CONSTRAINT chk_ai_voice_profiles_intro_content_type
        CHECK (
            intro_audio_content_type IS NULL
            OR intro_audio_content_type = 'audio/mpeg'
        ),
    ADD CONSTRAINT chk_ai_voice_profiles_intro_size
        CHECK (
            intro_audio_size_bytes IS NULL
            OR intro_audio_size_bytes BETWEEN 1 AND 5242880
        ),
    ADD CONSTRAINT chk_ai_voice_profiles_intro_duration
        CHECK (
            intro_audio_duration_ms IS NULL
            OR intro_audio_duration_ms BETWEEN 1 AND 30000
        ),
    ADD CONSTRAINT chk_ai_voice_profiles_intro_location
        CHECK (
            (
                intro_audio_bucket IS NULL
                AND intro_audio_object_key IS NULL
                AND intro_audio_content_type IS NULL
                AND intro_audio_size_bytes IS NULL
                AND intro_audio_duration_ms IS NULL
            )
            OR
            (
                intro_audio_bucket IS NOT NULL
                AND intro_audio_object_key IS NOT NULL
                AND intro_audio_content_type IS NOT NULL
                AND intro_audio_size_bytes IS NOT NULL
                AND intro_audio_duration_ms IS NOT NULL
            )
        ),
    ADD CONSTRAINT chk_ai_voice_profiles_intro_object_key
        CHECK (
            intro_audio_object_key IS NULL
            OR intro_audio_object_key LIKE 'voice-intros/%.mp3'
        );
