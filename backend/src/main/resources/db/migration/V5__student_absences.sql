CREATE TABLE IF NOT EXISTS student_absences (
    id BIGINT PRIMARY KEY,
    student_username VARCHAR(100) NOT NULL,
    student_name VARCHAR(200) NOT NULL,
    class_id BIGINT NOT NULL,
    class_name VARCHAR(50) NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_name VARCHAR(150) NOT NULL,
    absence_date VARCHAR(20) NOT NULL,
    teacher_username VARCHAR(100) NOT NULL,
    teacher_name VARCHAR(200) NOT NULL,
    motivated BOOLEAN NOT NULL DEFAULT FALSE,
    motivated_by_username VARCHAR(100),
    motivated_by_name VARCHAR(200),
    motivated_at VARCHAR(20),
    version INTEGER NOT NULL,
    CONSTRAINT fk_student_absences_class FOREIGN KEY (class_id) REFERENCES school_classes (id)
);

CREATE INDEX IF NOT EXISTS idx_student_absences_student_username ON student_absences (student_username);
CREATE INDEX IF NOT EXISTS idx_student_absences_class_id ON student_absences (class_id);
CREATE INDEX IF NOT EXISTS idx_student_absences_teacher_username ON student_absences (teacher_username);
