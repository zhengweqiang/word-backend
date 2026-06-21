ALTER TABLE meta_words
    ADD COLUMN IF NOT EXISTS syllable_detail JSONB;

COMMENT ON COLUMN meta_words.syllable_detail IS
    'Ordered syllable spelling, IPA, and optional UK/US audio';
