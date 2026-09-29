-- 기존 walk_records를 포함하여 순서대로 실행
-- 실제 GPS 경로(walk_record_points)와 추천 경로를 별도로 저장

ALTER TABLE walk_records
    ADD COLUMN planned_title VARCHAR(100) NULL,
    ADD COLUMN planned_description VARCHAR(1000) NULL,
    ADD COLUMN planned_distance_m BIGINT NULL,
    ADD COLUMN planned_estimated_minutes INT NULL;

CREATE TABLE walk_record_planned_points (
    walk_record_planned_point_id BIGINT NOT NULL AUTO_INCREMENT,
    walk_record_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,

    PRIMARY KEY (walk_record_planned_point_id),
    CONSTRAINT uk_walk_planned_point_sequence UNIQUE (walk_record_id, sequence_no),
    CONSTRAINT fk_walk_record_planned_points_walk_record
        FOREIGN KEY (walk_record_id)
        REFERENCES walk_records(walk_record_id)
);
