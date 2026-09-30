-- =========================================================
-- 7. 동행 모집
-- walk_meetings
-- =========================================================

CREATE TABLE walk_meetings (
    meeting_id BIGINT NOT NULL AUTO_INCREMENT,

    host_user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,

    title VARCHAR(150) NOT NULL,
    description TEXT NULL,

    meeting_date DATE NOT NULL,
    meeting_time TIME NOT NULL,

    max_participants INT NOT NULL,

    pet_required BOOLEAN NOT NULL DEFAULT FALSE,
    participation_condition VARCHAR(500) NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'RECRUITING',

    started_at DATETIME NULL,
    ended_at DATETIME NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (meeting_id),

    CONSTRAINT fk_walk_meetings_host_user
        FOREIGN KEY (host_user_id)
        REFERENCES users(user_id),

    CONSTRAINT fk_walk_meetings_course
        FOREIGN KEY (course_id)
        REFERENCES courses(course_id)
);