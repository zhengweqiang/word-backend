CREATE TABLE student_point_rule_audits (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL,
    operator_id BIGINT NOT NULL,
    operator_role VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    before_snapshot TEXT,
    after_snapshot TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_student_point_rule_audits_rule_created
    ON student_point_rule_audits (rule_id, created_at DESC);

CREATE INDEX idx_student_point_rule_audits_operator_created
    ON student_point_rule_audits (operator_id, created_at DESC);
