ALTER TABLE study_records
    ADD COLUMN points_eligible BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE study_day_tasks
    ADD COLUMN points_eligible BOOLEAN NOT NULL DEFAULT FALSE;
