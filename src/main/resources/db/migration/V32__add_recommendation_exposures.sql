CREATE TABLE recommendation_exposures (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    last_exposed_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_recommendation_exposures PRIMARY KEY (id),
    CONSTRAINT uk_recommendation_exposures_requester_target
        UNIQUE (requester_user_id, target_user_id),
    CONSTRAINT fk_recommendation_exposures_requester
        FOREIGN KEY (requester_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_recommendation_exposures_target
        FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_recommendation_exposures_different_users
        CHECK (requester_user_id <> target_user_id)
);

CREATE INDEX idx_recommendation_exposures_requester_last_exposed
    ON recommendation_exposures (requester_user_id, last_exposed_at);
