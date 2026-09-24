-- =========================================================
-- 4. 추천 산책 코스 좌표
-- course_points
-- =========================================================

CREATE TABLE course_points (
    course_point_id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,

    sequence_no INT NOT NULL,

    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,

    PRIMARY KEY (course_point_id),

    CONSTRAINT uk_course_points_course_sequence
        UNIQUE (course_id, sequence_no),

    CONSTRAINT fk_course_points_course
        FOREIGN KEY (course_id)
        REFERENCES courses(course_id)
);