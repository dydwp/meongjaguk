-- =========================================================
-- 9. 알림 (선택 구현)
-- notifications
-- =========================================================

CREATE TABLE notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,

    user_id BIGINT NOT NULL,

    type VARCHAR(30) NOT NULL,
    reference_id BIGINT NULL,

    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,

    is_read BOOLEAN NOT NULL DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (notification_id),

    CONSTRAINT fk_notifications_user
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
);