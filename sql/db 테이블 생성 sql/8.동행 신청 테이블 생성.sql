-- =========================================================
-- 5. 동행 신청
-- walk_applications
-- =========================================================

CREATE TABLE walk_applications (
    application_id BIGINT NOT NULL AUTO_INCREMENT,

    meeting_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    message VARCHAR(500) NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (application_id),

    CONSTRAINT uk_walk_applications_meeting_user
        UNIQUE (meeting_id, user_id),

    CONSTRAINT fk_walk_applications_meeting
        FOREIGN KEY (meeting_id)
        REFERENCES walk_meetings(meeting_id),

    CONSTRAINT fk_walk_applications_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);