ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS father_initial VARCHAR(1);
