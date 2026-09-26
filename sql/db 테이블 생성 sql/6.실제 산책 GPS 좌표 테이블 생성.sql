-- =========================================================
-- 6. 실제 산책 GPS 좌표
-- walk_record_points
-- =========================================================

CREATE TABLE walk_record_points (
    walk_record_point_id BIGINT NOT NULL AUTO_INCREMENT,
    walk_record_id BIGINT NOT NULL,

    sequence_no INT NOT NULL,

    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,

    recorded_at DATETIME NOT NULL,

    PRIMARY KEY (walk_record_point_id),

    INDEX idx_walk_record_points_sequence (sequence_no),

    CONSTRAINT fk_walk_record_points_walk_record
        FOREIGN KEY (walk_record_id)
        REFERENCES walk_records(walk_record_id)
);