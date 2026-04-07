CREATE TABLE IF NOT EXISTS timetable_entries (
    id BIGINT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    class_name VARCHAR(50) NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_name VARCHAR(150) NOT NULL,
    room_id BIGINT NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    teacher_username VARCHAR(100) NOT NULL,
    teacher_name VARCHAR(200) NOT NULL,
    weekday INTEGER NOT NULL,
    index_in_day INTEGER NOT NULL,
    version INTEGER NOT NULL,
    CONSTRAINT fk_timetable_entries_class FOREIGN KEY (class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_timetable_entries_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_timetable_entries_room FOREIGN KEY (room_id) REFERENCES rooms (id),
    CONSTRAINT fk_timetable_entries_teacher FOREIGN KEY (teacher_username) REFERENCES user_profiles (username)
);

CREATE INDEX IF NOT EXISTS idx_timetable_entries_class_slot
    ON timetable_entries (class_id, weekday, index_in_day);

CREATE INDEX IF NOT EXISTS idx_timetable_entries_teacher_slot
    ON timetable_entries (teacher_username, weekday, index_in_day);

CREATE TABLE IF NOT EXISTS student_grades (
    id BIGINT PRIMARY KEY,
    student_username VARCHAR(100) NOT NULL,
    student_name VARCHAR(200) NOT NULL,
    class_id BIGINT NOT NULL,
    class_name VARCHAR(50) NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_name VARCHAR(150) NOT NULL,
    grade_value INTEGER NOT NULL,
    grade_date VARCHAR(20) NOT NULL,
    teacher_username VARCHAR(100) NOT NULL,
    teacher_name VARCHAR(200) NOT NULL,
    version INTEGER NOT NULL,
    CONSTRAINT fk_student_grades_student FOREIGN KEY (student_username) REFERENCES user_profiles (username),
    CONSTRAINT fk_student_grades_class FOREIGN KEY (class_id) REFERENCES school_classes (id),
    CONSTRAINT fk_student_grades_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_student_grades_teacher FOREIGN KEY (teacher_username) REFERENCES user_profiles (username)
);

CREATE INDEX IF NOT EXISTS idx_student_grades_student_subject
    ON student_grades (student_username, subject_name, grade_date DESC, id DESC);

CREATE TABLE IF NOT EXISTS app_notifications (
    id BIGSERIAL PRIMARY KEY,
    recipient_username VARCHAR(100) NOT NULL,
    title VARCHAR(160) NOT NULL DEFAULT 'Actualizare in platforma',
    category VARCHAR(40) NOT NULL DEFAULT 'system',
    action_path VARCHAR(255),
    message VARCHAR(500) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_app_notifications_recipient FOREIGN KEY (recipient_username) REFERENCES user_profiles (username)
);

ALTER TABLE IF EXISTS user_profile_settings
    ADD COLUMN IF NOT EXISTS in_app_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE IF EXISTS app_notifications
    ADD COLUMN IF NOT EXISTS title VARCHAR(160) NOT NULL DEFAULT 'Actualizare in platforma';

ALTER TABLE IF EXISTS app_notifications
    ADD COLUMN IF NOT EXISTS category VARCHAR(40) NOT NULL DEFAULT 'system';

ALTER TABLE IF EXISTS app_notifications
    ADD COLUMN IF NOT EXISTS action_path VARCHAR(255);

ALTER TABLE IF EXISTS app_notifications
    ADD COLUMN IF NOT EXISTS read_at TIMESTAMPTZ;

UPDATE user_profile_settings
SET in_app_notifications_enabled = TRUE
WHERE in_app_notifications_enabled IS NULL;

UPDATE app_notifications
SET title = COALESCE(title, 'Actualizare in platforma'),
    category = COALESCE(category, 'system')
WHERE title IS NULL
   OR category IS NULL;

CREATE INDEX IF NOT EXISTS idx_app_notifications_recipient_created_at
    ON app_notifications (recipient_username, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_app_notifications_recipient_is_read
    ON app_notifications (recipient_username, is_read);
