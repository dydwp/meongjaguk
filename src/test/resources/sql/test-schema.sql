-- MySQL TIMESTAMP(date, time) 대체 (마이페이지 조회 SQL에서 사용)
CREATE ALIAS IF NOT EXISTS timestamp FOR 'com.meongjaguk.app.support.H2Functions.timestamp';

-- 엔티티가 없어 JPA가 만들지 않는 테이블 (sql/db 테이블 생성 sql/12번과 같은 구조)
CREATE TABLE IF NOT EXISTS walk_record_pets (
    walk_record_id BIGINT NOT NULL,
    pet_id BIGINT NOT NULL,
    PRIMARY KEY (walk_record_id, pet_id),
    CONSTRAINT fk_walk_record_pets_record
        FOREIGN KEY (walk_record_id) REFERENCES walk_records(walk_record_id) ON DELETE CASCADE,
    CONSTRAINT fk_walk_record_pets_pet
        FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE
);

-- 엔티티가 없어 JPA가 만들지 않는 테이블 (sql/db 테이블 생성 sql/13번과 같은 구조)
CREATE TABLE IF NOT EXISTS walk_meeting_pets (
    meeting_id BIGINT NOT NULL,
    pet_id BIGINT NOT NULL,
    PRIMARY KEY (meeting_id, pet_id),
    CONSTRAINT fk_walk_meeting_pets_meeting
        FOREIGN KEY (meeting_id) REFERENCES walk_meetings(meeting_id) ON DELETE CASCADE,
    CONSTRAINT fk_walk_meeting_pets_pet
        FOREIGN KEY (pet_id) REFERENCES pets(pet_id) ON DELETE CASCADE
);
