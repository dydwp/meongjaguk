-- =========================================================
-- 13. 동행 모집 - 반려견 연결
-- walk_meeting_pets
-- : 동행 모집 글 ↔ 모집자가 함께 데려가는 반려견
-- 하나의 모집 글에 여러 반려견을 연결하기 위한 테이블
-- =========================================================

CREATE TABLE walk_meeting_pets (
    meeting_id BIGINT NOT NULL,
    pet_id BIGINT NOT NULL,

    PRIMARY KEY (meeting_id, pet_id),

    INDEX idx_walk_meeting_pets_pet_id (pet_id),

    CONSTRAINT fk_walk_meeting_pets_meeting
        FOREIGN KEY (meeting_id)
        REFERENCES walk_meetings(meeting_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_walk_meeting_pets_pet
        FOREIGN KEY (pet_id)
        REFERENCES pets(pet_id)
        ON DELETE CASCADE
);