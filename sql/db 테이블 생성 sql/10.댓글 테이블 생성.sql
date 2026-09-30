-- =========================================================
-- 10. 댓글
-- walk_meeting_comments
-- =========================================================

CREATE TABLE walk_meeting_comments (
    comment_id BIGINT NOT NULL AUTO_INCREMENT,

    meeting_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    content VARCHAR(500) NOT NULL,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (comment_id),

    CONSTRAINT fk_walk_meeting_comments_meeting
        FOREIGN KEY (meeting_id)
        REFERENCES walk_meetings(meeting_id),

    CONSTRAINT fk_walk_meeting_comments_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);