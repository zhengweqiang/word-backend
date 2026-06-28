ALTER TABLE classroom_group_feed_messages
    DROP CONSTRAINT IF EXISTS ck_classroom_group_feed_messages_type;

ALTER TABLE classroom_group_feed_messages
    ADD CONSTRAINT ck_classroom_group_feed_messages_type
        CHECK (message_type IN ('TEXT', 'DICTIONARY', 'STUDY_PLAN', 'VIDEO'));

CREATE UNIQUE INDEX IF NOT EXISTS uk_classroom_group_feed_study_plan_once
    ON classroom_group_feed_messages(classroom_id, resource_id)
    WHERE message_type = 'STUDY_PLAN';
