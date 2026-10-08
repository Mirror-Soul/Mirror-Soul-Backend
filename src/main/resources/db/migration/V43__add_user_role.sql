ALTER TABLE users
    ADD COLUMN account_role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE users
    ADD CONSTRAINT chk_users_account_role CHECK (account_role IN ('USER', 'ADMIN'));
