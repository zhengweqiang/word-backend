ALTER TABLE video_storage_configs
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(32) NOT NULL DEFAULT 'TENCENT_VOD';

ALTER TABLE video_storage_configs
    ADD COLUMN IF NOT EXISTS space_name VARCHAR(128);

ALTER TABLE video_storage_configs
    ADD CONSTRAINT ck_video_storage_configs_provider_type
    CHECK (provider_type IN ('TENCENT_VOD', 'VOLCENGINE_VOD'));
