ALTER TABLE face_files
    ADD COLUMN object_key VARCHAR(500) NULL;

UPDATE face_files
SET object_key = file_url
WHERE object_key IS NULL;

ALTER TABLE face_files
    MODIFY COLUMN object_key VARCHAR(500) NOT NULL;

ALTER TABLE face_files
    ADD CONSTRAINT uk_face_files_user UNIQUE (user_id);
