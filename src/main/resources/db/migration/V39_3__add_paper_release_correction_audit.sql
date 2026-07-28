ALTER TABLE paper_release_targets
    ADD COLUMN source_classroom_ids_json TEXT NOT NULL DEFAULT '[]';

UPDATE paper_release_targets
SET source_classroom_ids_json = CASE
    WHEN source_classroom_id IS NULL THEN '[]'
    ELSE '[' || source_classroom_id || ']'
END;

ALTER TABLE paper_releases
    ADD COLUMN superseded_at TIMESTAMP,
    ADD COLUMN superseded_by_user_id BIGINT,
    ADD COLUMN supersede_reason VARCHAR(500);

ALTER TABLE paper_releases
    ADD CONSTRAINT fk_paper_releases_superseded_by_user
        FOREIGN KEY (superseded_by_user_id) REFERENCES users(id) ON DELETE RESTRICT;
