ALTER TABLE paper_template_questions
    ADD COLUMN removed_at TIMESTAMP;

ALTER TABLE paper_template_questions
    DROP CONSTRAINT ck_paper_template_questions_order;

ALTER TABLE paper_template_questions
    ADD CONSTRAINT ck_paper_template_questions_order CHECK (
        (removed_at IS NULL AND question_order > 0)
        OR (removed_at IS NOT NULL AND question_order < 0)
    );
