-- =========================================================
-- 12. 산책 기록 - 반려견 연결
-- 한 번의 산책에 여러 반려견을 연결하기 위한 테이블
-- =========================================================

CREATE TABLE walk_record_pets (
    walk_record_id BIGINT NOT NULL,
    pet_id BIGINT NOT NULL,

    PRIMARY KEY (walk_record_id, pet_id),
    INDEX idx_walk_record_pets_pet_id (pet_id),

    CONSTRAINT fk_walk_record_pets_record
        FOREIGN KEY (walk_record_id)
        REFERENCES walk_records(walk_record_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_walk_record_pets_pet
        FOREIGN KEY (pet_id)
        REFERENCES pets(pet_id)
        ON DELETE CASCADE
);