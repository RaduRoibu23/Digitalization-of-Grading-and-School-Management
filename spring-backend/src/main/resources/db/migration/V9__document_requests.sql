CREATE TABLE IF NOT EXISTS document_requests (
    id BIGSERIAL PRIMARY KEY,
    document_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_by_username VARCHAR(100) NOT NULL,
    student_username VARCHAR(100) NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    series VARCHAR(10),
    document_number INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,
    reviewed_by_username VARCHAR(100),
    resolution_note VARCHAR(255),
    snapshot_json TEXT,
    CONSTRAINT fk_document_requests_requested_by FOREIGN KEY (requested_by_username) REFERENCES user_profiles (username),
    CONSTRAINT fk_document_requests_student FOREIGN KEY (student_username) REFERENCES user_profiles (username),
    CONSTRAINT fk_document_requests_reviewed_by FOREIGN KEY (reviewed_by_username) REFERENCES user_profiles (username)
);

CREATE INDEX IF NOT EXISTS idx_document_requests_student_created
    ON document_requests (student_username, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_document_requests_status_created
    ON document_requests (status, created_at DESC, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_document_requests_type_number
    ON document_requests (document_type, document_number)
    WHERE document_number IS NOT NULL;
