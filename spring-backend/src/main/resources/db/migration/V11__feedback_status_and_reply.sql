ALTER TABLE feedback_entries
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'UNOPENED',
    ADD COLUMN IF NOT EXISTS status_updated_by_username VARCHAR(100),
    ADD COLUMN IF NOT EXISTS status_updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reply_message TEXT,
    ADD COLUMN IF NOT EXISTS replied_by_username VARCHAR(100),
    ADD COLUMN IF NOT EXISTS replied_at TIMESTAMPTZ;

UPDATE feedback_entries
SET status = 'UNOPENED'
WHERE status IS NULL OR BTRIM(status) = '';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_feedback_entries_status_updated_by'
    ) THEN
        ALTER TABLE feedback_entries
            ADD CONSTRAINT fk_feedback_entries_status_updated_by
                FOREIGN KEY (status_updated_by_username) REFERENCES user_profiles (username);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_feedback_entries_replied_by'
    ) THEN
        ALTER TABLE feedback_entries
            ADD CONSTRAINT fk_feedback_entries_replied_by
                FOREIGN KEY (replied_by_username) REFERENCES user_profiles (username);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_feedback_entries_status_created
    ON feedback_entries (status, created_at DESC, id DESC);
