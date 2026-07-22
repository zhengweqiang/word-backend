ALTER TABLE study_records
    ADD COLUMN request_key VARCHAR(64);

UPDATE study_records
SET request_key = 'legacy:' || id
WHERE request_key IS NULL;

ALTER TABLE study_records
    ALTER COLUMN request_key SET NOT NULL,
    ADD CONSTRAINT uk_study_records_request_key UNIQUE (request_key);
