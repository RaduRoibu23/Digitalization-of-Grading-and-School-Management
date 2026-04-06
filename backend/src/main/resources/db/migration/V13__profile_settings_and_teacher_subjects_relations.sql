CREATE TABLE IF NOT EXISTS user_profile_settings (
    profile_id BIGINT PRIMARY KEY,
    email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_user_profile_settings_profile FOREIGN KEY (profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);

INSERT INTO user_profile_settings (profile_id, email_notifications_enabled)
SELECT up.id, TRUE
FROM user_profiles up
WHERE NOT EXISTS (
    SELECT 1
    FROM user_profile_settings ups
    WHERE ups.profile_id = up.id
);

CREATE TABLE IF NOT EXISTS user_profile_subject_links (
    profile_id BIGINT NOT NULL,
    subject_id BIGINT NOT NULL,
    CONSTRAINT fk_user_profile_subject_links_profile FOREIGN KEY (profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_profile_subject_links_subject FOREIGN KEY (subject_id) REFERENCES subjects (id) ON DELETE CASCADE
);

INSERT INTO user_profile_subject_links (profile_id, subject_id)
SELECT DISTINCT ups.profile_id, s.id
FROM user_profile_subjects ups
JOIN subjects s ON s.name = ups.subject_name
WHERE NOT EXISTS (
    SELECT 1
    FROM user_profile_subject_links link
    WHERE link.profile_id = ups.profile_id
      AND link.subject_id = s.id
);

CREATE INDEX IF NOT EXISTS idx_user_profile_settings_profile_id
    ON user_profile_settings (profile_id);

CREATE INDEX IF NOT EXISTS idx_user_profile_subject_links_profile_id
    ON user_profile_subject_links (profile_id);

CREATE INDEX IF NOT EXISTS idx_user_profile_subject_links_subject_id
    ON user_profile_subject_links (subject_id);
