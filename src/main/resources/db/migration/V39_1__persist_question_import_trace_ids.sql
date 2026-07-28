ALTER TABLE question_import_preview_rows
    ADD COLUMN dictionary_id BIGINT,
    ADD COLUMN meta_word_id BIGINT,
    ADD CONSTRAINT fk_question_import_preview_rows_dictionary
        FOREIGN KEY (dictionary_id) REFERENCES dictionaries(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_question_import_preview_rows_meta_word
        FOREIGN KEY (meta_word_id) REFERENCES meta_words(id) ON DELETE RESTRICT;
