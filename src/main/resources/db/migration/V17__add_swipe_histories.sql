CREATE TABLE swipe_histories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    swiper_user_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT pk_swipe_histories PRIMARY KEY (id),
    CONSTRAINT fk_swipe_histories_swiper
        FOREIGN KEY (swiper_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_swipe_histories_target
        FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_swipe_histories_different_users
        CHECK (swiper_user_id <> target_user_id)
);

CREATE INDEX idx_swipe_histories_swiper_target_created
    ON swipe_histories (swiper_user_id, target_user_id, created_at);
