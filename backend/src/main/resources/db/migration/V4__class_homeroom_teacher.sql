ALTER TABLE school_classes
    ADD COLUMN homeroom_teacher_username VARCHAR(100),
    ADD COLUMN homeroom_teacher_name VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS ux_school_classes_homeroom_teacher_username
    ON school_classes (homeroom_teacher_username)
    WHERE homeroom_teacher_username IS NOT NULL;
