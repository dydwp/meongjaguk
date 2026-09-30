-- =========================================================
-- 5. 개인 산책 기록
-- walk_records
-- =========================================================

CREATE TABLE walk_records (
    walk_record_id BIGINT NOT NULL AUTO_INCREMENT,

    user_id BIGINT NOT NULL,
    course_id BIGINT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',

    started_at DATETIME NOT NULL,
    ended_at DATETIME NULL,

    duration_seconds INT NULL,
    distance_m INT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (walk_record_id),

    CONSTRAINT fk_walk_records_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_walk_records_course
        FOREIGN KEY (course_id)
        REFERENCES courses(course_id)
);