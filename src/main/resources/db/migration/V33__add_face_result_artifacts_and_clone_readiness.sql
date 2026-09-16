ALTER TABLE face_training_jobs ADD COLUMN error_code VARCHAR(100) NULL;
ALTER TABLE face_training_jobs ADD COLUMN error_retryable BOOLEAN NULL;

ALTER TABLE ai_face_profiles ADD COLUMN bucket VARCHAR(100) NULL;
ALTER TABLE ai_face_profiles ADD COLUMN profile_key VARCHAR(500) NULL;
ALTER TABLE ai_face_profiles ADD COLUMN portrait_key VARCHAR(500) NULL;
ALTER TABLE ai_face_profiles ADD COLUMN manifest_key VARCHAR(500) NULL;
ALTER TABLE ai_face_profiles ADD COLUMN quality_gate_passed BOOLEAN NULL;

ALTER TABLE clones ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE clones ADD COLUMN personality_training_completed BOOLEAN NOT NULL DEFAULT FALSE;
