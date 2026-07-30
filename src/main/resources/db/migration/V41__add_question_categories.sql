CREATE TABLE question_categories (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    last_modified_by_user_id BIGINT,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_question_categories_active_name
    ON question_categories (lower(name))
    WHERE deleted_at IS NULL;

CREATE INDEX idx_question_categories_deleted_at
    ON question_categories (deleted_at);

ALTER TABLE question_bank_items
    ADD COLUMN category VARCHAR(100);

ALTER TABLE question_import_preview_rows
    ADD COLUMN category VARCHAR(100);

ALTER TABLE paper_template_questions
    ADD COLUMN category VARCHAR(100);

ALTER TABLE paper_release_questions
    ADD COLUMN category VARCHAR(100);

CREATE INDEX idx_question_bank_items_category
    ON question_bank_items (category);

CREATE INDEX idx_question_import_preview_rows_category
    ON question_import_preview_rows (category);

CREATE INDEX idx_paper_template_questions_category
    ON paper_template_questions (category);

CREATE INDEX idx_paper_release_questions_category
    ON paper_release_questions (category);
