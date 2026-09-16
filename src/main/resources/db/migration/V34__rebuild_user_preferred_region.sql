-- V1에서 생성되고 V27 이후 롤백용으로 남아 있던 테이블을
-- 단일 앵커 + 주변 동 개수 구조로 전환한다.
-- 기존 선호 정보는 user_preferred_sigungu에 이미 보존되어 있다.
DELETE FROM user_preferred_region;

ALTER TABLE user_preferred_region
    DROP FOREIGN KEY fk_upr_region,
    DROP INDEX uk_user_region;

ALTER TABLE user_preferred_region
    CHANGE COLUMN region_id anchor_region_id BIGINT NOT NULL,
    ADD COLUMN nearby_count INT NOT NULL AFTER anchor_region_id,
    ADD COLUMN updated_at DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD CONSTRAINT fk_upr_anchor_region
        FOREIGN KEY (anchor_region_id) REFERENCES region(id) ON DELETE CASCADE,
    ADD CONSTRAINT uk_user_preferred_region_user UNIQUE (user_id),
    ADD CONSTRAINT chk_user_preferred_region_nearby_count CHECK (nearby_count >= 1);
