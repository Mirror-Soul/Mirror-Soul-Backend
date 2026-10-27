CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    store VARCHAR(10) NOT NULL,
    identity_hash VARCHAR(64) NOT NULL,
    reference_ciphertext TEXT NOT NULL,
    user_id BIGINT NULL,
    store_transaction_id VARCHAR(200) NULL,
    product_id VARCHAR(200) NULL,
    unit_seconds INT NOT NULL DEFAULT 0,
    quantity INT NOT NULL DEFAULT 0,
    granted_seconds INT NOT NULL DEFAULT 0,
    revoked_seconds INT NOT NULL DEFAULT 0,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    grant_status VARCHAR(20) NOT NULL DEFAULT 'NOT_GRANTED',
    refund_status VARCHAR(20) NOT NULL DEFAULT 'NONE',
    consumption_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    refund_event_at BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_payments_identity UNIQUE(identity_hash),
    CONSTRAINT uk_payments_transaction UNIQUE(store, store_transaction_id),
    CONSTRAINT fk_payments_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT ck_payments_seconds CHECK(granted_seconds >= 0 AND revoked_seconds >= 0 AND revoked_seconds <= granted_seconds)
);
CREATE INDEX idx_payments_user_created ON payments(user_id, created_at, id);
CREATE INDEX idx_payments_status ON payments(refund_status, verification_status, id);

ALTER TABLE talk_time_transactions ADD COLUMN payment_id BIGINT NULL;
ALTER TABLE talk_time_transactions ADD COLUMN operation_key VARCHAR(100) NULL;
ALTER TABLE talk_time_transactions ADD COLUMN actor_id BIGINT NULL;
ALTER TABLE talk_time_transactions ADD COLUMN memo VARCHAR(500) NULL;
ALTER TABLE talk_time_transactions ADD COLUMN request_hash VARCHAR(64) NULL;
ALTER TABLE talk_time_transactions ADD CONSTRAINT uk_talk_time_operation UNIQUE(operation_key);
ALTER TABLE talk_time_transactions ADD CONSTRAINT fk_talk_time_payment FOREIGN KEY(payment_id) REFERENCES payments(id);
CREATE INDEX idx_talk_time_payment ON talk_time_transactions(payment_id, id);

-- Preserve historical balances where legacy writes had no corresponding journal entry.
-- After this migration all mutations derive their balance from the journal.
INSERT INTO talk_time_transactions(user_id, reason, delta_seconds, balance_after_seconds, operation_key, memo)
SELECT u.id, 'OPENING_BALANCE', u.remaining_talk_time - COALESCE(t.balance, 0),
       u.remaining_talk_time, CONCAT('migration-v51:', u.id), 'Legacy balance reconciliation'
FROM users u
LEFT JOIN (SELECT user_id, SUM(delta_seconds) AS balance FROM talk_time_transactions GROUP BY user_id) t ON t.user_id = u.id
WHERE u.remaining_talk_time <> COALESCE(t.balance, 0);

CREATE TABLE billing_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(64) NOT NULL,
    payment_id BIGINT NULL,
    action VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    occurred_at BIGINT NOT NULL,
    store_transaction_id VARCHAR(200) NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    lease_until DATETIME NULL,
    claim_token VARCHAR(36) NULL,
    failure_code VARCHAR(80) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_billing_job_event UNIQUE(event_key),
    CONSTRAINT fk_billing_job_payment FOREIGN KEY(payment_id) REFERENCES payments(id)
);
CREATE INDEX idx_billing_jobs_queue ON billing_jobs(state, next_attempt_at, lease_until, id);
CREATE INDEX idx_billing_jobs_payment ON billing_jobs(payment_id, id);
CREATE TABLE billing_audits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NULL,
    ledger_id BIGINT NULL,
    job_id BIGINT NULL,
    actor_id BIGINT NULL,
    action VARCHAR(40) NOT NULL,
    operation_key VARCHAR(100) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_billing_audit_operation UNIQUE(operation_key),
    CONSTRAINT fk_billing_audit_payment FOREIGN KEY(payment_id) REFERENCES payments(id),
    CONSTRAINT fk_billing_audit_ledger FOREIGN KEY(ledger_id) REFERENCES talk_time_transactions(id),
    CONSTRAINT fk_billing_audit_job FOREIGN KEY(job_id) REFERENCES billing_jobs(id)
);
CREATE INDEX idx_billing_audits_payment ON billing_audits(payment_id, id);

ALTER TABLE video_calls ADD COLUMN connected_at DATETIME(3) NULL;
ALTER TABLE video_calls ADD COLUMN billing_deadline_at DATETIME(3) NULL;
ALTER TABLE video_calls ADD COLUMN billing_limit_seconds INT NULL;
ALTER TABLE video_calls ADD COLUMN billing_end_reason VARCHAR(30) NULL;
ALTER TABLE video_calls ADD COLUMN billing_end_source VARCHAR(10) NULL;
-- Historical completed calls retain their original duration. Ongoing legacy calls retain their start.
UPDATE video_calls SET connected_at = started_at WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_video_calls_billing_expiry ON video_calls(status, billing_deadline_at, id);
CREATE TABLE call_billing_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_key VARCHAR(64) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    call_id BIGINT NOT NULL,
    kind VARCHAR(10) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_call_billing_event UNIQUE(event_key),
    CONSTRAINT fk_call_billing_event_call FOREIGN KEY(call_id) REFERENCES video_calls(id)
);
CREATE INDEX idx_call_billing_event_call ON call_billing_events(call_id, id);
