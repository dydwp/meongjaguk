-- MySQL TIMESTAMP(date, time) 대체 (마이페이지 조회 SQL에서 사용)
CREATE ALIAS IF NOT EXISTS timestamp FOR 'com.mungjaguk.app.support.H2Functions.timestamp';

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
