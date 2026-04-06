ALTER TABLE feedback_entries
    ALTER COLUMN submitted_by_username DROP NOT NULL;

ALTER TABLE feedback_entries
    ADD COLUMN IF NOT EXISTS source VARCHAR(30) NOT NULL DEFAULT 'HELP';

UPDATE feedback_entries
SET source = 'HELP'
WHERE source IS NULL OR BTRIM(source) = '';

CREATE INDEX IF NOT EXISTS idx_feedback_entries_source_created
    ON feedback_entries (source, created_at DESC, id DESC);
