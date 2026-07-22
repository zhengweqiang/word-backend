CREATE TABLE student_point_accounts (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    available_points INT NOT NULL DEFAULT 0,
    frozen_points INT NOT NULL DEFAULT 0,
    lifetime_earned_points INT NOT NULL DEFAULT 0,
    lifetime_spent_points INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_point_accounts_student UNIQUE (student_id),
    CONSTRAINT fk_student_point_accounts_student
        FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_point_accounts_available CHECK (available_points >= 0),
    CONSTRAINT ck_student_point_accounts_frozen CHECK (frozen_points >= 0),
    CONSTRAINT ck_student_point_accounts_lifetime_earned CHECK (lifetime_earned_points >= 0),
    CONSTRAINT ck_student_point_accounts_lifetime_spent CHECK (lifetime_spent_points >= 0),
    CONSTRAINT ck_student_point_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN'))
);

CREATE TABLE student_point_transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    balance_before INT NOT NULL,
    balance_after INT NOT NULL,
    frozen_before INT NOT NULL DEFAULT 0,
    frozen_after INT NOT NULL DEFAULT 0,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT,
    source_key VARCHAR(200),
    rule_code VARCHAR(64),
    idempotency_key VARCHAR(160) NOT NULL,
    operator_id BIGINT,
    operator_role VARCHAR(32),
    reason VARCHAR(500),
    reversed_transaction_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_point_transactions_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_student_point_transactions_amount CHECK (amount <> 0),
    CONSTRAINT ck_student_point_transactions_type CHECK (
        transaction_type IN ('EARN', 'DEDUCT', 'FREEZE', 'UNFREEZE', 'SPEND', 'REVERSE')
    ),
    CONSTRAINT ck_student_point_transactions_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'VIDEO_WATCH', 'EXAM',
            'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    )
);

CREATE INDEX idx_student_point_transactions_student_created
    ON student_point_transactions (student_id, created_at DESC);
CREATE INDEX idx_student_point_transactions_source
    ON student_point_transactions (source_type, source_id);
CREATE INDEX idx_student_point_transactions_source_key
    ON student_point_transactions (source_key);
CREATE INDEX idx_student_point_transactions_operator_created
    ON student_point_transactions (operator_id, created_at DESC);

CREATE TABLE student_point_events (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    source_id BIGINT,
    source_key VARCHAR(200),
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(100),
    points INT NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    auto_attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP,
    processing_trigger_type VARCHAR(32),
    processing_operator_id BIGINT,
    processing_operator_role VARCHAR(32),
    processing_reason VARCHAR(500),
    processing_started_at TIMESTAMP,
    last_error VARCHAR(1000),
    operator_id BIGINT,
    operator_role VARCHAR(32),
    reason VARCHAR(500),
    transaction_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    CONSTRAINT uk_student_point_events_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_student_point_events_transaction
        FOREIGN KEY (transaction_id) REFERENCES student_point_transactions(id) ON DELETE RESTRICT,
    CONSTRAINT ck_student_point_events_points CHECK (points <> 0),
    CONSTRAINT ck_student_point_events_auto_attempt_count CHECK (auto_attempt_count >= 0),
    CONSTRAINT ck_student_point_events_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_student_point_events_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'VIDEO_WATCH', 'EXAM',
            'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    ),
    CONSTRAINT ck_student_point_events_processing_trigger CHECK (
        processing_trigger_type IS NULL OR processing_trigger_type IN ('AUTO', 'MANUAL')
    )
);

CREATE INDEX idx_student_point_events_status_retry
    ON student_point_events (status, next_retry_at);
CREATE INDEX idx_student_point_events_student_created
    ON student_point_events (student_id, created_at DESC);
CREATE INDEX idx_student_point_events_source
    ON student_point_events (source_type, source_id);
CREATE INDEX idx_student_point_events_source_key
    ON student_point_events (source_key);

CREATE TABLE student_point_event_attempts (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    attempt_no INT NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    operator_id BIGINT,
    operator_role VARCHAR(32),
    reason VARCHAR(500),
    error_message VARCHAR(1000),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    CONSTRAINT fk_student_point_event_attempts_event
        FOREIGN KEY (event_id) REFERENCES student_point_events(id) ON DELETE RESTRICT,
    CONSTRAINT uk_student_point_event_attempts_event_no UNIQUE (event_id, attempt_no),
    CONSTRAINT ck_student_point_event_attempts_no CHECK (attempt_no > 0),
    CONSTRAINT ck_student_point_event_attempts_trigger CHECK (trigger_type IN ('AUTO', 'MANUAL')),
    CONSTRAINT ck_student_point_event_attempts_status CHECK (status IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_student_point_event_attempts_event_started
    ON student_point_event_attempts (event_id, started_at DESC);
CREATE INDEX idx_student_point_event_attempts_trigger_started
    ON student_point_event_attempts (trigger_type, started_at DESC);

CREATE TABLE student_point_rules (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    source_type VARCHAR(64) NOT NULL,
    base_points INT NOT NULL,
    scope_type VARCHAR(32) NOT NULL DEFAULT 'GLOBAL',
    scope_id BIGINT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_student_point_rules_code UNIQUE (code),
    CONSTRAINT ck_student_point_rules_base_points CHECK (base_points <> 0),
    CONSTRAINT ck_student_point_rules_source_type CHECK (
        source_type IN (
            'STUDY_TASK', 'STUDY_RECORD', 'VIDEO_WATCH', 'EXAM',
            'MANUAL_ADJUSTMENT', 'ADMIN_CORRECTION', 'REDEMPTION'
        )
    )
);

CREATE TABLE student_point_adjustment_requests (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    amount INT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_by BIGINT NOT NULL,
    requested_role VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    transaction_id BIGINT,
    reverse_transaction_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    reversed_at TIMESTAMP,
    CONSTRAINT ck_student_point_adjustments_amount CHECK (amount <> 0),
    CONSTRAINT ck_student_point_adjustments_status CHECK (
        status IN ('PENDING', 'APPLIED', 'FAILED', 'REJECTED', 'REVERSED')
    )
);

CREATE INDEX idx_student_point_adjustments_student_created
    ON student_point_adjustment_requests (student_id, created_at DESC);
CREATE INDEX idx_student_point_adjustments_status_created
    ON student_point_adjustment_requests (status, created_at DESC);

INSERT INTO student_point_rules (code, name, description, source_type, base_points)
VALUES
    ('STUDY_RECORD_CORRECT', '单词答对', '每次正确学习记录奖励积分', 'STUDY_RECORD', 1),
    ('DAILY_TASK_COMPLETED', '完成每日任务', '首次完成每日学习任务奖励积分', 'STUDY_TASK', 10)
ON CONFLICT (code) DO NOTHING;

INSERT INTO student_point_accounts (student_id)
SELECT id
FROM users
WHERE role = 'STUDENT'
ON CONFLICT (student_id) DO NOTHING;
