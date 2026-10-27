ALTER TABLE job_verification_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE job_verification_requests ADD COLUMN evidence_expires_at DATETIME;
ALTER TABLE job_verification_requests ADD COLUMN evidence_deleted_at DATETIME;
ALTER TABLE job_verification_requests ADD COLUMN evidence_delete_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE job_verification_requests ADD COLUMN evidence_delete_error VARCHAR(40);
ALTER TABLE job_verification_requests ADD COLUMN notification_state VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE job_verification_requests ADD COLUMN notification_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE job_verification_requests ADD COLUMN notification_updated_at DATETIME;
ALTER TABLE job_verification_requests ADD COLUMN notification_error VARCHAR(40);
UPDATE job_verification_requests SET evidence_expires_at = CASE WHEN reviewed_at IS NULL
 THEN TIMESTAMPADD(DAY,90,created_at) ELSE TIMESTAMPADD(DAY,30,reviewed_at) END;
ALTER TABLE job_verification_requests MODIFY COLUMN evidence_expires_at DATETIME NOT NULL;
CREATE INDEX idx_job_evidence_expiry ON job_verification_requests(evidence_deleted_at,evidence_expires_at);
CREATE INDEX idx_job_notification_queue ON job_verification_requests(notification_state,notification_updated_at);
CREATE INDEX idx_job_review_history ON job_verification_requests(status,reviewed_at,id);
