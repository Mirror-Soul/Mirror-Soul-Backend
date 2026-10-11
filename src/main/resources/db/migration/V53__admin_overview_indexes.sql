CREATE INDEX idx_safety_overview_period ON safety_reports(priority,created_at,state,id);
CREATE INDEX idx_support_overview_period ON support_tickets(status,created_at,id);
CREATE INDEX idx_training_activity ON training_lifecycle_events(training_job_id,accepted,created_at);
