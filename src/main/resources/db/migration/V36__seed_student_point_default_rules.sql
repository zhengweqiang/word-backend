INSERT INTO student_point_rules (
    code,
    name,
    description,
    source_type,
    base_points,
    scope_type,
    scope_id,
    enabled
)
VALUES
    (
        'STUDY_RECORD_CORRECT',
        '单词答对',
        '每次正确学习记录奖励积分',
        'STUDY_RECORD',
        1,
        'GLOBAL',
        NULL,
        TRUE
    ),
    (
        'DAILY_TASK_COMPLETED',
        '完成每日任务',
        '首次完成每日学习任务奖励积分',
        'STUDY_TASK',
        10,
        'GLOBAL',
        NULL,
        TRUE
    ),
    (
        'VIDEO_WATCH',
        '视频学习',
        NULL,
        'VIDEO_WATCH',
        2,
        'GLOBAL',
        NULL,
        TRUE
    ),
    (
        'EXAM',
        '考试',
        NULL,
        'EXAM',
        10,
        'EXAM',
        NULL,
        TRUE
    ),
    (
        'ADMIN_CORRECTION',
        '人工冲正',
        NULL,
        'ADMIN_CORRECTION',
        2,
        'GLOBAL',
        NULL,
        TRUE
    ),
    (
        'MANUAL_ADJUSTMENT',
        '人工调整',
        NULL,
        'MANUAL_ADJUSTMENT',
        1,
        'GLOBAL',
        NULL,
        TRUE
    ),
    (
        'REDEMPTION',
        '兑换积分',
        NULL,
        'REDEMPTION',
        5,
        'GLOBAL',
        NULL,
        TRUE
    )
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    source_type = EXCLUDED.source_type,
    base_points = EXCLUDED.base_points,
    scope_type = EXCLUDED.scope_type,
    scope_id = EXCLUDED.scope_id,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;
