CREATE TABLE IF NOT EXISTS announcements (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(160) NOT NULL DEFAULT 'Anunt intern',
    message VARCHAR(1200) NOT NULL,
    created_by_username VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_announcements_created_by FOREIGN KEY (created_by_username) REFERENCES user_profiles (username)
);

CREATE INDEX IF NOT EXISTS idx_announcements_created_at
    ON announcements (created_at DESC, id DESC);
