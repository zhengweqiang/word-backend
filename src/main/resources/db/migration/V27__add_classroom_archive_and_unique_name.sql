ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE classrooms
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_classrooms_status') THEN
        ALTER TABLE classrooms
            ADD CONSTRAINT ck_classrooms_status CHECK (status IN ('ACTIVE', 'ARCHIVED'));
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM classrooms
        GROUP BY name
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot add unique classroom name constraint while duplicate classroom names exist';
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_classrooms_name') THEN
        ALTER TABLE classrooms ADD CONSTRAINT uk_classrooms_name UNIQUE (name);
    END IF;
END $$;
