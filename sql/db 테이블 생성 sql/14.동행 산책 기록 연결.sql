-- =========================================================
-- 14. 동행 산책 기록 연결
-- walk_records -> walk_meetings
-- =========================================================

ALTER TABLE walk_records
    ADD COLUMN meeting_id BIGINT NULL,
    ADD CONSTRAINT uk_walk_records_meeting UNIQUE (meeting_id),
    ADD CONSTRAINT fk_walk_records_meeting
        FOREIGN KEY (meeting_id)
        REFERENCES walk_meetings(meeting_id);