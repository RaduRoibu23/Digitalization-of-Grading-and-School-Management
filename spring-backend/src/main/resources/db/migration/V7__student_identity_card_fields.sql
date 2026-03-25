ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS id_series VARCHAR(2),
    ADD COLUMN IF NOT EXISTS serial_number VARCHAR(6);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profiles_identity_card
    ON user_profiles (id_series, serial_number)
    WHERE id_series IS NOT NULL AND serial_number IS NOT NULL;
