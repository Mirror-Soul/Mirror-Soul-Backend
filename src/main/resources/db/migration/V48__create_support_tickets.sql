CREATE TABLE support_tickets (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, version BIGINT NOT NULL DEFAULT 0, activity_sequence BIGINT NOT NULL DEFAULT 0,
 member_id BIGINT NOT NULL, channel VARCHAR(20) NOT NULL, category VARCHAR(20) NOT NULL,
 status VARCHAR(30) NOT NULL, subject VARCHAR(200) NOT NULL, assignee_id BIGINT,
 waiting_since DATETIME, created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 transaction_id BIGINT, call_id BIGINT, job_type VARCHAR(30), job_id BIGINT,
 request_key VARCHAR(100) NOT NULL, request_hash VARCHAR(64) NOT NULL, external_message_id VARCHAR(200),
 CONSTRAINT uk_support_member_request UNIQUE(member_id,request_key),
 CONSTRAINT uk_support_external_message UNIQUE(external_message_id),
 CONSTRAINT fk_support_member FOREIGN KEY(member_id) REFERENCES users(id),
 CONSTRAINT fk_support_assignee FOREIGN KEY(assignee_id) REFERENCES users(id)
);
CREATE INDEX idx_support_queue ON support_tickets(status,waiting_since,id);
CREATE INDEX idx_support_assignee ON support_tickets(assignee_id,status,id);
CREATE INDEX idx_support_member ON support_tickets(member_id,created_at,id);
CREATE TABLE support_entries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, ticket_id BIGINT NOT NULL, author_id BIGINT NOT NULL,
 type VARCHAR(30) NOT NULL, body TEXT NOT NULL, request_key VARCHAR(140) NOT NULL, request_hash VARCHAR(64) NOT NULL,
 delivery VARCHAR(20) NOT NULL, failure_code VARCHAR(40), attempts INT NOT NULL DEFAULT 0,
 attempt_token VARCHAR(36), attempted_at DATETIME, created_at DATETIME NOT NULL,
 CONSTRAINT uk_support_entry_request UNIQUE(ticket_id,request_key),
 CONSTRAINT fk_support_entry_ticket FOREIGN KEY(ticket_id) REFERENCES support_tickets(id)
);
CREATE INDEX idx_support_entry_history ON support_entries(ticket_id,id);
CREATE INDEX idx_support_delivery ON support_entries(delivery,attempted_at,id);
CREATE TABLE support_retries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, entry_id BIGINT NOT NULL, request_key VARCHAR(100) NOT NULL,
 author_id BIGINT NOT NULL, acknowledged BOOLEAN NOT NULL DEFAULT FALSE,
 CONSTRAINT uk_support_retry UNIQUE(entry_id,request_key),
 CONSTRAINT fk_support_retry_entry FOREIGN KEY(entry_id) REFERENCES support_entries(id)
);
CREATE TABLE support_attachments (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, ticket_id BIGINT NOT NULL, owner_id BIGINT NOT NULL,
 object_key VARCHAR(250) NOT NULL, file_name VARCHAR(150) NOT NULL, content_type VARCHAR(50) NOT NULL,
 size BIGINT NOT NULL, confirmed BOOLEAN NOT NULL DEFAULT FALSE, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 object_version VARCHAR(200), etag VARCHAR(200), upload_expires_at DATETIME NOT NULL, expires_at DATETIME NOT NULL,
 CONSTRAINT fk_support_attachment_ticket FOREIGN KEY(ticket_id) REFERENCES support_tickets(id)
);
CREATE INDEX idx_support_attachment_expiry ON support_attachments(deleted,expires_at,id);
CREATE INDEX idx_support_attachment_ticket ON support_attachments(ticket_id,id);
