ALTER TABLE users ADD COLUMN safety_restricted_until DATETIME NULL;

CREATE TABLE safety_reports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    reporter_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL,
    call_id BIGINT NULL,
    message_id BIGINT NULL,
    description TEXT NULL,
    message_snapshot TEXT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    context_hash VARCHAR(64) NOT NULL,
    assignee_id BIGINT NULL,
    latest_decision_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    evidence_expires_at DATETIME NOT NULL,
    evidence_purged BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_safety_report_request UNIQUE(reporter_id,request_key),
    CONSTRAINT fk_safety_report_reporter FOREIGN KEY(reporter_id) REFERENCES users(id),
    CONSTRAINT fk_safety_report_target FOREIGN KEY(target_id) REFERENCES users(id),
    CONSTRAINT fk_safety_report_assignee FOREIGN KEY(assignee_id) REFERENCES users(id)
);
CREATE INDEX idx_safety_report_queue ON safety_reports(state,priority,assignee_id,id);
CREATE INDEX idx_safety_report_duplicates ON safety_reports(reporter_id,context_hash,created_at);
CREATE INDEX idx_safety_report_expiry ON safety_reports(evidence_purged,evidence_expires_at);

CREATE TABLE safety_decisions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(30) NOT NULL,
    rationale VARCHAR(1000) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_version BIGINT NOT NULL DEFAULT 0,
    target_version BIGINT NOT NULL DEFAULT 0,
    sanction_id BIGINT NULL,
    reporter_notice_id BIGINT NULL,
    target_notice_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_safety_decision_request UNIQUE(report_id,request_key),
    CONSTRAINT fk_safety_decision_report FOREIGN KEY(report_id) REFERENCES safety_reports(id),
    CONSTRAINT fk_safety_decision_actor FOREIGN KEY(actor_id) REFERENCES users(id)
);
CREATE INDEX idx_safety_decision_report ON safety_decisions(report_id,id);

CREATE TABLE safety_sanctions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    decision_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    started_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    released_at DATETIME NULL,
    released_by BIGINT NULL,
    CONSTRAINT uk_safety_sanction_report UNIQUE(report_id),
    CONSTRAINT fk_safety_sanction_report FOREIGN KEY(report_id) REFERENCES safety_reports(id),
    CONSTRAINT fk_safety_sanction_decision FOREIGN KEY(decision_id) REFERENCES safety_decisions(id),
    CONSTRAINT fk_safety_sanction_user FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE INDEX idx_safety_sanction_active ON safety_sanctions(user_id,released_at,expires_at);

CREATE TABLE safety_appeals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    decision_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    explanation VARCHAR(2000) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    state VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    resolved_at DATETIME NULL,
    resolved_by BIGINT NULL,
    CONSTRAINT uk_safety_appeal_decision UNIQUE(decision_id),
    CONSTRAINT fk_safety_appeal_report FOREIGN KEY(report_id) REFERENCES safety_reports(id),
    CONSTRAINT fk_safety_appeal_decision FOREIGN KEY(decision_id) REFERENCES safety_decisions(id),
    CONSTRAINT fk_safety_appeal_user FOREIGN KEY(user_id) REFERENCES users(id)
);
CREATE INDEX idx_safety_appeal_user ON safety_appeals(user_id,id);
CREATE INDEX idx_safety_appeal_pending ON safety_appeals(report_id,state);

CREATE TABLE safety_notices (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    decision_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    reason VARCHAR(30) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL,
    read_at DATETIME NULL,
    CONSTRAINT fk_safety_notice_user FOREIGN KEY(user_id) REFERENCES users(id),
    CONSTRAINT fk_safety_notice_report FOREIGN KEY(report_id) REFERENCES safety_reports(id),
    CONSTRAINT fk_safety_notice_decision FOREIGN KEY(decision_id) REFERENCES safety_decisions(id)
);
CREATE INDEX idx_safety_notice_user ON safety_notices(user_id,id);

CREATE TABLE safety_evidence_audits (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    operation VARCHAR(20) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    allowed BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL
);
CREATE INDEX idx_safety_evidence_audit ON safety_evidence_audits(report_id,id);

CREATE TABLE safety_call_diagnostics (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    call_id BIGINT NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    code VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT uk_safety_diagnostic_event UNIQUE(call_id,event_key)
);
CREATE INDEX idx_safety_diagnostic_expiry ON safety_call_diagnostics(created_at);
