ALTER TABLE user_profiles
    ADD COLUMN IF NOT EXISTS is_external BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_profiles
SET role = 'director'
WHERE role = 'admin';

CREATE TABLE IF NOT EXISTS grade_change_requests (
    id BIGSERIAL PRIMARY KEY,
    grade_id BIGINT NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    base_grade_version INTEGER NOT NULL,
    proposed_grade_value INTEGER,
    proposed_grade_date VARCHAR(20),
    proposed_comment TEXT,
    reason VARCHAR(255) NOT NULL,
    requested_by_username VARCHAR(100) NOT NULL,
    reviewed_by_username VARCHAR(100),
    resolution_note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    CONSTRAINT fk_grade_change_requests_grade FOREIGN KEY (grade_id) REFERENCES student_grades (id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_change_requests_requested_by FOREIGN KEY (requested_by_username) REFERENCES user_profiles (username),
    CONSTRAINT fk_grade_change_requests_reviewed_by FOREIGN KEY (reviewed_by_username) REFERENCES user_profiles (username)
);

CREATE INDEX IF NOT EXISTS idx_grade_change_requests_grade_created
    ON grade_change_requests (grade_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_grade_change_requests_status_created
    ON grade_change_requests (status, created_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_grade_change_requests_pending_grade
    ON grade_change_requests (grade_id)
    WHERE status = 'PENDING';
