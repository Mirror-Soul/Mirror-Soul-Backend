-- Keep legacy totals and leave components/version NULL until all four scores are recalculated.
ALTER TABLE clones MODIFY COLUMN sync_rate DECIMAL(4,1) NOT NULL DEFAULT 0.0;
ALTER TABLE clones ADD COLUMN face_similarity_score DECIMAL(5,2) NULL;
ALTER TABLE clones ADD COLUMN voice_similarity_score DECIMAL(5,2) NULL;
ALTER TABLE clones ADD COLUMN profile_similarity_score DECIMAL(5,2) NULL;
ALTER TABLE clones ADD COLUMN data_reliability_score DECIMAL(5,2) NULL;
ALTER TABLE clones ADD COLUMN similarity_penalty DECIMAL(5,2) NOT NULL DEFAULT 0;
ALTER TABLE clones ADD COLUMN similarity_score_version VARCHAR(50) NULL;
ALTER TABLE clones ADD COLUMN similarity_face_job_id BIGINT NULL;
ALTER TABLE clones ADD COLUMN similarity_voice_job_id BIGINT NULL;
ALTER TABLE clones ADD COLUMN similarity_profile_revision BIGINT NULL;
