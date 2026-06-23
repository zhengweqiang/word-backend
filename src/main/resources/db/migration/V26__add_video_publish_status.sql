ALTER TABLE video_assets
    ADD COLUMN IF NOT EXISTS publish_status VARCHAR(20) NOT NULL DEFAULT 'UNPUBLISHED';

ALTER TABLE video_assets
    ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;

ALTER TABLE video_assets
    ADD COLUMN IF NOT EXISTS unpublished_at TIMESTAMP;

ALTER TABLE video_assets
    ADD CONSTRAINT ck_video_assets_publish_status
    CHECK (publish_status IN ('UNPUBLISHED', 'PUBLISHED'));

CREATE INDEX IF NOT EXISTS idx_video_assets_publish_status
    ON video_assets(publish_status);

CREATE INDEX IF NOT EXISTS idx_video_assets_student_visible
    ON video_assets(publish_status, status, updated_at DESC);
