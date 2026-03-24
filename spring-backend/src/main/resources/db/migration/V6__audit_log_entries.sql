CREATE TABLE IF NOT EXISTS audit_log_entries (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(120) NOT NULL,
    actor_username VARCHAR(100) NOT NULL,
    effect TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entries_created_at
    ON audit_log_entries (created_at DESC, id DESC);
