CREATE TABLE IF NOT EXISTS school_classes (
    id BIGINT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    profile VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS subjects (
    id BIGINT PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS rooms (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    capacity INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS user_profiles (
    id BIGINT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(50) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    class_id BIGINT NULL,
    CONSTRAINT fk_user_profiles_class FOREIGN KEY (class_id) REFERENCES school_classes (id)
);

CREATE TABLE IF NOT EXISTS user_profile_subjects (
    profile_id BIGINT NOT NULL,
    subject_name VARCHAR(150) NOT NULL,
    CONSTRAINT fk_user_profile_subjects_profile FOREIGN KEY (profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_role ON user_profiles (role);
CREATE INDEX IF NOT EXISTS idx_user_profiles_class_id ON user_profiles (class_id);
CREATE INDEX IF NOT EXISTS idx_user_profile_subjects_profile_id ON user_profile_subjects (profile_id);
