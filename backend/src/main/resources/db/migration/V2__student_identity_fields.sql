ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS address VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cnp VARCHAR(13);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profiles_cnp
    ON user_profiles (cnp)
    WHERE cnp IS NOT NULL;
