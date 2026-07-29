ALTER TABLE users
    ADD COLUMN residence_region_id BIGINT NULL,
    ADD INDEX idx_users_residence_region_id (residence_region_id);

UPDATE users u
JOIN region r
  ON TRIM(u.region) = CONCAT(
      r.sido_name,
      ' ',
      r.sigungu_name,
      ' ',
      r.eupmyeondong_name
  )
SET u.residence_region_id = r.id
WHERE u.region IS NOT NULL
  AND TRIM(u.region) <> '';

CREATE TEMPORARY TABLE residence_region_migration_guard (
    user_id BIGINT NOT NULL,
    residence_region_id BIGINT NOT NULL
);

INSERT INTO residence_region_migration_guard (user_id, residence_region_id)
SELECT u.id, u.residence_region_id
FROM users u
WHERE u.region IS NOT NULL
  AND TRIM(u.region) <> '';

DROP TEMPORARY TABLE residence_region_migration_guard;

ALTER TABLE users
    ADD CONSTRAINT fk_users_residence_region
        FOREIGN KEY (residence_region_id)
        REFERENCES region(id)
        ON DELETE SET NULL,
    DROP COLUMN region;
