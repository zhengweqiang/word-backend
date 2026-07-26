ALTER TABLE student_point_transactions DROP CONSTRAINT IF EXISTS ck_student_point_transactions_source_type;
ALTER TABLE student_point_events DROP CONSTRAINT IF EXISTS ck_student_point_events_source_type;
ALTER TABLE student_point_rules DROP CONSTRAINT IF EXISTS ck_student_point_rules_source_type;

ALTER TABLE student_point_accounts
    ALTER COLUMN available_points TYPE NUMERIC(19,2) USING available_points::numeric,
    ALTER COLUMN frozen_points TYPE NUMERIC(19,2) USING frozen_points::numeric,
    ALTER COLUMN lifetime_earned_points TYPE NUMERIC(19,2) USING lifetime_earned_points::numeric,
    ALTER COLUMN lifetime_spent_points TYPE NUMERIC(19,2) USING lifetime_spent_points::numeric;

ALTER TABLE student_point_transactions
    ALTER COLUMN amount TYPE NUMERIC(19,2) USING amount::numeric,
    ALTER COLUMN balance_before TYPE NUMERIC(19,2) USING balance_before::numeric,
    ALTER COLUMN balance_after TYPE NUMERIC(19,2) USING balance_after::numeric,
    ALTER COLUMN frozen_before TYPE NUMERIC(19,2) USING frozen_before::numeric,
    ALTER COLUMN frozen_after TYPE NUMERIC(19,2) USING frozen_after::numeric;

ALTER TABLE student_point_events
    ALTER COLUMN points TYPE NUMERIC(19,2) USING points::numeric;

ALTER TABLE student_point_rules
    ALTER COLUMN base_points TYPE NUMERIC(19,2) USING base_points::numeric;

ALTER TABLE student_point_adjustment_requests
    ALTER COLUMN amount TYPE NUMERIC(19,2) USING amount::numeric;

ALTER TABLE student_point_transactions
    ADD CONSTRAINT ck_student_point_transactions_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'CLASSROOM_CHAT', 'VIDEO_WATCH', 'EXAM',
            'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    );

ALTER TABLE student_point_events
    ADD CONSTRAINT ck_student_point_events_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'CLASSROOM_CHAT', 'VIDEO_WATCH', 'EXAM',
            'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    );

ALTER TABLE student_point_rules
    ADD CONSTRAINT ck_student_point_rules_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'CLASSROOM_CHAT', 'VIDEO_WATCH', 'EXAM',
            'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    );

INSERT INTO student_point_rules (code, name, description, source_type, base_points, scope_type, scope_id, enabled)
VALUES (
    'CLASSROOM_CHAT_CONTRIBUTION',
    '班级聊天贡献',
    '学生在班级聊天中留言一次获得积分',
    'CLASSROOM_CHAT',
    0.50,
    'GLOBAL',
    NULL,
    TRUE
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    source_type = EXCLUDED.source_type,
    base_points = EXCLUDED.base_points,
    scope_type = EXCLUDED.scope_type,
    scope_id = EXCLUDED.scope_id,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;
