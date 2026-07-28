CREATE TABLE question_import_batches (
    id BIGSERIAL PRIMARY KEY,
    imported_by_user_id BIGINT NOT NULL,
    file_name VARCHAR(255),
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    duplicate_rows INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PREVIEWED',
    confirmed_at TIMESTAMP,
    expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_question_import_batches_imported_by
        FOREIGN KEY (imported_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_question_import_batches_counts CHECK (
        total_rows >= 0 AND valid_rows >= 0 AND invalid_rows >= 0 AND duplicate_rows >= 0
    ),
    CONSTRAINT ck_question_import_batches_status CHECK (status IN ('PREVIEWED', 'CONFIRMED', 'EXPIRED'))
);

CREATE INDEX idx_question_import_batches_imported_by
    ON question_import_batches (imported_by_user_id);
CREATE INDEX idx_question_import_batches_status
    ON question_import_batches (status);

CREATE TABLE question_bank_items (
    id BIGSERIAL PRIMARY KEY,
    question_type VARCHAR(32) NOT NULL,
    stem TEXT NOT NULL,
    options_json TEXT,
    accepted_answers_json TEXT NOT NULL,
    default_score NUMERIC(19,2) NOT NULL,
    difficulty INT,
    tags TEXT,
    explanation TEXT,
    dictionary_id BIGINT,
    meta_word_id BIGINT,
    source_question_id BIGINT,
    import_batch_id BIGINT,
    created_by_user_id BIGINT NOT NULL,
    imported_by_user_id BIGINT,
    last_modified_by_user_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP,
    CONSTRAINT fk_question_bank_items_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_bank_items_meta_word
        FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_bank_items_source_question
        FOREIGN KEY (source_question_id) REFERENCES question_bank_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_bank_items_import_batch
        FOREIGN KEY (import_batch_id) REFERENCES question_import_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_bank_items_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_bank_items_imported_by
        FOREIGN KEY (imported_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_bank_items_modified_by
        FOREIGN KEY (last_modified_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_question_bank_items_type CHECK (
        question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'FILL_IN_BLANK')
    ),
    CONSTRAINT ck_question_bank_items_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_question_bank_items_score CHECK (default_score > 0)
);

CREATE INDEX idx_question_bank_items_type
    ON question_bank_items (question_type);
CREATE INDEX idx_question_bank_items_status
    ON question_bank_items (status);
CREATE INDEX idx_question_bank_items_created_by
    ON question_bank_items (created_by_user_id);
CREATE INDEX idx_question_bank_items_source_question
    ON question_bank_items (source_question_id);
CREATE INDEX idx_question_bank_items_import_batch
    ON question_bank_items (import_batch_id);

CREATE TABLE question_import_preview_rows (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    row_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    question_type VARCHAR(32),
    stem TEXT,
    options_json TEXT,
    accepted_answers_json TEXT,
    score NUMERIC(19,2),
    difficulty INT,
    tags TEXT,
    explanation TEXT,
    dictionary_name VARCHAR(255),
    word VARCHAR(255),
    message TEXT,
    duplicate_question_id BIGINT,
    raw_row_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_question_import_preview_rows_batch
        FOREIGN KEY (batch_id) REFERENCES question_import_batches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_question_import_preview_rows_duplicate
        FOREIGN KEY (duplicate_question_id) REFERENCES question_bank_items(id) ON DELETE RESTRICT,
    CONSTRAINT uk_question_import_preview_rows_batch_row UNIQUE (batch_id, row_number),
    CONSTRAINT ck_question_import_preview_rows_status CHECK (
        status IN ('VALID', 'INVALID', 'DUPLICATE_CANDIDATE')
    ),
    CONSTRAINT ck_question_import_preview_rows_type CHECK (
        question_type IS NULL OR question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'FILL_IN_BLANK')
    )
);

CREATE INDEX idx_question_import_preview_rows_batch
    ON question_import_preview_rows (batch_id);
CREATE INDEX idx_question_import_preview_rows_status
    ON question_import_preview_rows (status);

CREATE TABLE paper_templates (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    instructions TEXT,
    owner_user_id BIGINT NOT NULL,
    source_paper_id BIGINT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options BOOLEAN NOT NULL DEFAULT FALSE,
    total_score NUMERIC(19,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    archived_at TIMESTAMP,
    CONSTRAINT fk_paper_templates_owner
        FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_templates_source
        FOREIGN KEY (source_paper_id) REFERENCES paper_templates(id) ON DELETE RESTRICT,
    CONSTRAINT ck_paper_templates_status CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED')),
    CONSTRAINT ck_paper_templates_total_score CHECK (total_score >= 0)
);

CREATE INDEX idx_paper_templates_owner
    ON paper_templates (owner_user_id);
CREATE INDEX idx_paper_templates_status
    ON paper_templates (status);
CREATE INDEX idx_paper_templates_source
    ON paper_templates (source_paper_id);

CREATE TABLE paper_template_questions (
    id BIGSERIAL PRIMARY KEY,
    paper_template_id BIGINT NOT NULL,
    source_question_id BIGINT,
    question_order INT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    stem TEXT NOT NULL,
    options_json TEXT,
    accepted_answers_json TEXT NOT NULL,
    explanation TEXT,
    score NUMERIC(19,2) NOT NULL,
    dictionary_id BIGINT,
    meta_word_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_paper_template_questions_template
        FOREIGN KEY (paper_template_id) REFERENCES paper_templates(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_template_questions_source
        FOREIGN KEY (source_question_id) REFERENCES question_bank_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_template_questions_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_template_questions_meta_word
        FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE RESTRICT,
    CONSTRAINT uk_paper_template_questions_order UNIQUE (paper_template_id, question_order),
    CONSTRAINT ck_paper_template_questions_type CHECK (
        question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'FILL_IN_BLANK')
    ),
    CONSTRAINT ck_paper_template_questions_order CHECK (question_order > 0),
    CONSTRAINT ck_paper_template_questions_score CHECK (score > 0)
);

CREATE INDEX idx_paper_template_questions_template
    ON paper_template_questions (paper_template_id);
CREATE INDEX idx_paper_template_questions_source
    ON paper_template_questions (source_question_id);

CREATE TABLE paper_releases (
    id BIGSERIAL PRIMARY KEY,
    paper_template_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions TEXT,
    published_by_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    question_count INT NOT NULL DEFAULT 0,
    total_score NUMERIC(19,2) NOT NULL DEFAULT 0,
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_options BOOLEAN NOT NULL DEFAULT FALSE,
    start_time TIMESTAMP,
    deadline TIMESTAMP,
    blank_answer_policy VARCHAR(32) NOT NULL DEFAULT 'ALLOW_BLANK',
    result_visibility VARCHAR(32) NOT NULL DEFAULT 'SCORE_ONLY',
    results_released_at TIMESTAMP,
    results_released_by_user_id BIGINT,
    withdrawn_at TIMESTAMP,
    withdrawn_by_user_id BIGINT,
    withdraw_reason VARCHAR(500),
    invalidated_at TIMESTAMP,
    invalidated_by_user_id BIGINT,
    invalidate_reason VARCHAR(500),
    supersedes_release_id BIGINT,
    superseded_by_release_id BIGINT,
    show_superseded_to_students BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_paper_releases_template
        FOREIGN KEY (paper_template_id) REFERENCES paper_templates(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_releases_published_by
        FOREIGN KEY (published_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_releases_results_released_by
        FOREIGN KEY (results_released_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_releases_withdrawn_by
        FOREIGN KEY (withdrawn_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_releases_invalidated_by
        FOREIGN KEY (invalidated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_releases_supersedes
        FOREIGN KEY (supersedes_release_id) REFERENCES paper_releases(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_releases_superseded_by
        FOREIGN KEY (superseded_by_release_id) REFERENCES paper_releases(id) ON DELETE RESTRICT,
    CONSTRAINT ck_paper_releases_status CHECK (
        status IN ('SCHEDULED', 'OPEN', 'WITHDRAWN', 'INVALIDATED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_paper_releases_blank_policy CHECK (
        blank_answer_policy IN ('ALLOW_BLANK', 'REQUIRE_ALL_ANSWERED')
    ),
    CONSTRAINT ck_paper_releases_visibility CHECK (
        result_visibility IN ('HIDDEN_UNTIL_RELEASED', 'SCORE_ONLY', 'SCORE_AND_ANSWERS')
    ),
    CONSTRAINT ck_paper_releases_counts CHECK (question_count >= 0 AND total_score >= 0)
);

CREATE INDEX idx_paper_releases_template
    ON paper_releases (paper_template_id);
CREATE INDEX idx_paper_releases_published_by
    ON paper_releases (published_by_user_id);
CREATE INDEX idx_paper_releases_status
    ON paper_releases (status);
CREATE INDEX idx_paper_releases_supersedes
    ON paper_releases (supersedes_release_id);

CREATE TABLE paper_release_questions (
    id BIGSERIAL PRIMARY KEY,
    paper_release_id BIGINT NOT NULL,
    paper_template_question_id BIGINT,
    source_question_id BIGINT,
    question_order INT NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    stem TEXT NOT NULL,
    options_json TEXT,
    accepted_answers_json TEXT NOT NULL,
    explanation TEXT,
    score NUMERIC(19,2) NOT NULL,
    dictionary_id BIGINT,
    meta_word_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_paper_release_questions_release
        FOREIGN KEY (paper_release_id) REFERENCES paper_releases(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_release_questions_template_question
        FOREIGN KEY (paper_template_question_id) REFERENCES paper_template_questions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_release_questions_source
        FOREIGN KEY (source_question_id) REFERENCES question_bank_items(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_release_questions_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_release_questions_meta_word
        FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE RESTRICT,
    CONSTRAINT uk_paper_release_questions_order UNIQUE (paper_release_id, question_order),
    CONSTRAINT ck_paper_release_questions_type CHECK (
        question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'FILL_IN_BLANK')
    ),
    CONSTRAINT ck_paper_release_questions_order CHECK (question_order > 0),
    CONSTRAINT ck_paper_release_questions_score CHECK (score > 0)
);

CREATE INDEX idx_paper_release_questions_release
    ON paper_release_questions (paper_release_id);
CREATE INDEX idx_paper_release_questions_source
    ON paper_release_questions (source_question_id);

CREATE TABLE paper_release_targets (
    id BIGSERIAL PRIMARY KEY,
    paper_release_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    source_classroom_id BIGINT,
    targeted_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_paper_release_targets_release
        FOREIGN KEY (paper_release_id) REFERENCES paper_releases(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_release_targets_student
        FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_release_targets_classroom
        FOREIGN KEY (source_classroom_id) REFERENCES classrooms(id) ON DELETE RESTRICT,
    CONSTRAINT fk_paper_release_targets_targeted_by
        FOREIGN KEY (targeted_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_paper_release_targets_student UNIQUE (paper_release_id, student_id)
);

CREATE INDEX idx_paper_release_targets_release
    ON paper_release_targets (paper_release_id);
CREATE INDEX idx_paper_release_targets_student
    ON paper_release_targets (student_id);
CREATE INDEX idx_paper_release_targets_classroom
    ON paper_release_targets (source_classroom_id);

CREATE TABLE student_paper_attempts (
    id BIGSERIAL PRIMARY KEY,
    paper_release_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NOT_STARTED',
    answered_count INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    earned_score NUMERIC(19,2) NOT NULL DEFAULT 0,
    total_score NUMERIC(19,2) NOT NULL DEFAULT 0,
    score_percentage NUMERIC(5,2),
    opened_at TIMESTAMP,
    last_draft_saved_at TIMESTAMP,
    submitted_at TIMESTAMP,
    invalidated_at TIMESTAMP,
    invalidated_by_user_id BIGINT,
    invalidate_reason VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_paper_attempts_release
        FOREIGN KEY (paper_release_id) REFERENCES paper_releases(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_paper_attempts_student
        FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_paper_attempts_invalidated_by
        FOREIGN KEY (invalidated_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_student_paper_attempts_release_student UNIQUE (paper_release_id, student_id),
    CONSTRAINT ck_student_paper_attempts_status CHECK (
        status IN ('NOT_STARTED', 'IN_PROGRESS', 'SUBMITTED', 'OVERDUE', 'SUBMITTED_LATE', 'INVALIDATED')
    ),
    CONSTRAINT ck_student_paper_attempts_counts CHECK (
        answered_count >= 0 AND correct_count >= 0 AND earned_score >= 0 AND total_score >= 0
    )
);

CREATE INDEX idx_student_paper_attempts_release
    ON student_paper_attempts (paper_release_id);
CREATE INDEX idx_student_paper_attempts_student
    ON student_paper_attempts (student_id);
CREATE INDEX idx_student_paper_attempts_status
    ON student_paper_attempts (status);

CREATE TABLE student_paper_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    release_question_id BIGINT NOT NULL,
    selected_answers_json TEXT,
    blank_answers_json TEXT,
    is_correct BOOLEAN,
    earned_score NUMERIC(19,2),
    answered_at TIMESTAMP,
    finalized_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_paper_answers_attempt
        FOREIGN KEY (attempt_id) REFERENCES student_paper_attempts(id) ON DELETE RESTRICT,
    CONSTRAINT fk_student_paper_answers_release_question
        FOREIGN KEY (release_question_id) REFERENCES paper_release_questions(id) ON DELETE RESTRICT,
    CONSTRAINT uk_student_paper_answers_question UNIQUE (attempt_id, release_question_id),
    CONSTRAINT ck_student_paper_answers_score CHECK (earned_score IS NULL OR earned_score >= 0)
);

CREATE INDEX idx_student_paper_answers_attempt
    ON student_paper_answers (attempt_id);
CREATE INDEX idx_student_paper_answers_question
    ON student_paper_answers (release_question_id);
