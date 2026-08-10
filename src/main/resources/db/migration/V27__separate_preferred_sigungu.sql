CREATE TABLE sigungu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sido_name VARCHAR(50) NOT NULL,
    sigungu_name VARCHAR(50) NOT NULL,

    CONSTRAINT pk_sigungu PRIMARY KEY (id),
    CONSTRAINT uk_sigungu_names UNIQUE (sido_name, sigungu_name)
);

INSERT INTO sigungu (sido_name, sigungu_name)
SELECT DISTINCT sido_name, sigungu_name
FROM region;

CREATE TABLE user_preferred_sigungu (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    sigungu_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_preferred_sigungu PRIMARY KEY (id),
    CONSTRAINT fk_ups_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ups_sigungu
        FOREIGN KEY (sigungu_id) REFERENCES sigungu(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_sigungu UNIQUE (user_id, sigungu_id)
);

INSERT INTO user_preferred_sigungu (user_id, sigungu_id, created_at)
SELECT upr.user_id, s.id, MIN(upr.created_at)
FROM user_preferred_region upr
JOIN region r ON r.id = upr.region_id
JOIN sigungu s
  ON s.sido_name = r.sido_name
 AND s.sigungu_name = r.sigungu_name
GROUP BY upr.user_id, s.id;

-- 이전 애플리케이션 버전으로 롤백할 수 있도록 기존 테이블은 유지한다.
-- 새 구조가 안정화된 후 별도 마이그레이션에서 제거한다.
