ALTER TABLE video_assets
    RENAME COLUMN publish_status TO cloud_publish_status;

ALTER TABLE video_assets
    DROP CONSTRAINT IF EXISTS ck_video_assets_publish_status;

ALTER TABLE video_assets
    ADD CONSTRAINT ck_video_assets_cloud_publish_status
    CHECK (cloud_publish_status IN ('UNPUBLISHED', 'PUBLISHED'));

DROP INDEX IF EXISTS idx_video_assets_publish_status;
DROP INDEX IF EXISTS idx_video_assets_student_visible;

CREATE INDEX IF NOT EXISTS idx_video_assets_cloud_publish_status
    ON video_assets(cloud_publish_status);

CREATE INDEX IF NOT EXISTS idx_video_assets_student_visible
    ON video_assets(cloud_publish_status, status, updated_at DESC);

ALTER TABLE video_storage_configs
    ALTER COLUMN provider_type SET DEFAULT 'VOLCENGINE_VOD';
