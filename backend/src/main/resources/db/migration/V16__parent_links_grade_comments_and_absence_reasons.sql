ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS linked_student_username VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_user_profiles_linked_student'
    ) THEN
        ALTER TABLE user_profiles
            ADD CONSTRAINT fk_user_profiles_linked_student
                FOREIGN KEY (linked_student_username) REFERENCES user_profiles (username);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_profiles_linked_student_username
    ON user_profiles (linked_student_username)
    WHERE linked_student_username IS NOT NULL;

ALTER TABLE student_grades
    ADD COLUMN IF NOT EXISTS comment TEXT;

ALTER TABLE student_absences
    ADD COLUMN IF NOT EXISTS motivation_reason TEXT;
