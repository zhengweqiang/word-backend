CREATE TABLE IF NOT EXISTS classroom_group_feed_messages (
    id BIGSERIAL PRIMARY KEY,
    classroom_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    message_type VARCHAR(32) NOT NULL,
    content TEXT,
    resource_id BIGINT,
    resource_title VARCHAR(255),
    resource_summary TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_classroom_group_feed_messages_classroom
        FOREIGN KEY (classroom_id) REFERENCES classrooms(id) ON DELETE CASCADE,
    CONSTRAINT fk_classroom_group_feed_messages_author
        FOREIGN KEY (author_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT ck_classroom_group_feed_messages_type
        CHECK (message_type IN ('TEXT', 'DICTIONARY', 'VIDEO'))
);

CREATE INDEX IF NOT EXISTS idx_classroom_group_feed_messages_classroom_created_at
    ON classroom_group_feed_messages(classroom_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_classroom_group_feed_messages_classroom_type_created_at
    ON classroom_group_feed_messages(classroom_id, message_type, created_at DESC);
