ALTER TABLE region
    ADD COLUMN latitude DECIMAL(10, 7) NULL,
    ADD COLUMN longitude DECIMAL(10, 7) NULL,
    ADD COLUMN coordinate_source VARCHAR(30) NULL,
    ADD COLUMN coordinate_updated_at DATETIME NULL;
