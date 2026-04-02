CREATE TABLE IF NOT EXISTS feedback_entries (
    id BIGSERIAL PRIMARY KEY,
    submitted_by_username VARCHAR(100) NOT NULL,
    category VARCHAR(40) NOT NULL,
    satisfaction VARCHAR(20) NOT NULL,
    wants_contact BOOLEAN NOT NULL DEFAULT FALSE,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_entries_submitter FOREIGN KEY (submitted_by_username) REFERENCES user_profiles (username)
);

CREATE INDEX IF NOT EXISTS idx_feedback_entries_created
    ON feedback_entries (created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_feedback_entries_submitter_created
    ON feedback_entries (submitted_by_username, created_at DESC, id DESC);
