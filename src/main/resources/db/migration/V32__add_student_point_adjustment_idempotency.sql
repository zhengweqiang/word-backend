ALTER TABLE student_point_adjustment_requests
    ADD COLUMN request_key VARCHAR(64),
    ADD COLUMN replaces_request_id BIGINT,
    ADD COLUMN replaced_by_request_id BIGINT;

UPDATE student_point_adjustment_requests
SET request_key = 'legacy:' || id
WHERE request_key IS NULL;

ALTER TABLE student_point_adjustment_requests
    ALTER COLUMN request_key SET NOT NULL,
    ADD CONSTRAINT uk_student_point_adjustments_request_key UNIQUE (request_key);
