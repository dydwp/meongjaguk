-- =========================================================
-- 3. 추천 산책 코스
-- courses
-- =========================================================

CREATE TABLE courses (
    course_id BIGINT NOT NULL AUTO_INCREMENT,

    name VARCHAR(100) NOT NULL,
    description TEXT NULL,

    distance_m INT NOT NULL,
    estimated_minutes INT NOT NULL,

    feature VARCHAR(500) NULL,
    region VARCHAR(100) NULL,

    start_latitude DECIMAL(10, 7) NOT NULL,
    start_longitude DECIMAL(10, 7) NOT NULL,

    thumbnail_image VARCHAR(500) NULL,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (course_id)
);