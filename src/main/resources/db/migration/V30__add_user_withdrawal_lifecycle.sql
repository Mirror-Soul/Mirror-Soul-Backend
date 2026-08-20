ALTER TABLE users
    ADD COLUMN withdrawn_at DATETIME NULL,
    ADD COLUMN deleted_at DATETIME NULL;

UPDATE users
SET withdrawn_at = COALESCE(updated_at, CURRENT_TIMESTAMP)
WHERE status = 'INACTIVE'
  AND withdrawn_at IS NULL;

CREATE INDEX idx_users_status_withdrawn_at
    ON users (status, withdrawn_at);
