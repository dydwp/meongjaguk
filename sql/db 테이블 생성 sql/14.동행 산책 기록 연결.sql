-- 기존 walk_records, walk_meetings를 포함하여 순서대로 실행
-- 동행 산책에서 개최자가 기록한 산책을 모집글에 연결 (동행 산책이 아니면 NULL)
ALTER TABLE walk_records
    ADD COLUMN meeting_id BIGINT NULL,
    ADD CONSTRAINT uk_walk_records_meeting UNIQUE (meeting_id),
    ADD CONSTRAINT fk_walk_records_meeting
        FOREIGN KEY (meeting_id) REFERENCES walk_meetings(meeting_id);
