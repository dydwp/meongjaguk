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

-- 기존 walk_records, walk_meetings를 포함하여 순서대로 실행
-- 동행 산책에서 개최자가 기록한 산책을 모집글에 연결 (동행 산책이 아니면 NULL)
ALTER TABLE walk_records
    ADD COLUMN meeting_id BIGINT NULL,
    ADD CONSTRAINT uk_walk_records_meeting UNIQUE (meeting_id),
    ADD CONSTRAINT fk_walk_records_meeting
        FOREIGN KEY (meeting_id) REFERENCES walk_meetings(meeting_id);
