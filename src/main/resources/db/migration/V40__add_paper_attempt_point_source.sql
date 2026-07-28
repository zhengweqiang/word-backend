ALTER TABLE student_point_transactions
    DROP CONSTRAINT IF EXISTS ck_student_point_transactions_source_type;
ALTER TABLE student_point_events
    DROP CONSTRAINT IF EXISTS ck_student_point_events_source_type;
ALTER TABLE student_point_rules
    DROP CONSTRAINT IF EXISTS ck_student_point_rules_source_type;

ALTER TABLE student_point_transactions
    ADD CONSTRAINT ck_student_point_transactions_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'CLASSROOM_CHAT', 'VIDEO_WATCH', 'EXAM',
            'PAPER_RELEASE_ATTEMPT', 'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    );

ALTER TABLE student_point_events
    ADD CONSTRAINT ck_student_point_events_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'CLASSROOM_CHAT', 'VIDEO_WATCH', 'EXAM',
            'PAPER_RELEASE_ATTEMPT', 'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    );

ALTER TABLE student_point_rules
    ADD CONSTRAINT ck_student_point_rules_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'CLASSROOM_CHAT', 'VIDEO_WATCH', 'EXAM',
            'PAPER_RELEASE_ATTEMPT', 'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    );
