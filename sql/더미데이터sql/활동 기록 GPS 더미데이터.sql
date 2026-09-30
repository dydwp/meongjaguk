-- =========================================================
-- 활동 기록 / GPS 경로 더미데이터
--
-- 목적
-- 1. 마이페이지 활동 내역 목록 테스트
-- 2. 활동 상세 페이지 테스트
-- 3. GPS 산책 경로 지도 테스트
-- 4. 추천 코스 산책 / 자유 산책 테스트
-- 5. GPS 경로가 없는 활동 예외 화면 테스트
--
-- 실행 전제
-- 1. users 테이블에 테스트 회원이 존재해야 함
-- 2. 추천 코스 더미데이터가 먼저 입력되어 있어야 함
-- 3. @test_user_id 값을 실행 환경의 회원 ID에 맞게 수정
--
-- 주의
-- 동일한 테스트 데이터를 반복 실행하면 활동 기록이 중복될 수 있음
-- =========================================================


-- =========================================================
-- 테스트 회원 설정
-- 팀원별 로컬 DB의 테스트 회원 ID에 맞게 변경
-- =========================================================

SET @test_user_id = 1;


-- =========================================================
-- 추천 코스 ID 조회
-- course_id를 직접 지정하지 않고 코스명으로 조회
-- =========================================================

SET @course_seoul_forest = (
    SELECT course_id
    FROM courses
    WHERE name = '서울숲 반려견 산책 코스'
    LIMIT 1
);

SET @course_ttukseom = (
    SELECT course_id
    FROM courses
    WHERE name = '한강공원 뚝섬 산책 코스'
    LIMIT 1
);

SET @course_dream_forest = (
    SELECT course_id
    FROM courses
    WHERE name = '북서울꿈의숲 산책 코스'
    LIMIT 1
);

SET @course_yangjaecheon = (
    SELECT course_id
    FROM courses
    WHERE name = '양재천 산책 코스'
    LIMIT 1
);


-- =========================================================
-- 추천 코스 ID 확인
-- NULL이 나오면 추천 코스 더미데이터를 먼저 확인
-- =========================================================

SELECT
    @course_seoul_forest AS seoul_forest_course_id,
    @course_ttukseom AS ttukseom_course_id,
    @course_dream_forest AS dream_forest_course_id,
    @course_yangjaecheon AS yangjaecheon_course_id;


-- =========================================================
-- 1. 서울숲 반려견 산책 코스
-- GPS 경로 있음
-- =========================================================

INSERT INTO walk_records (
    user_id,
    course_id,
    status,
    started_at,
    ended_at,
    duration_seconds,
    distance_m
) VALUES (
    @test_user_id,
    @course_seoul_forest,
    'COMPLETED',
    '2026-09-25 19:00:00',
    '2026-09-25 19:48:12',
    2892,
    3100
);

SET @seoul_forest_record_id = LAST_INSERT_ID();

INSERT INTO walk_record_points (
    walk_record_id,
    sequence_no,
    latitude,
    longitude,
    recorded_at
) VALUES
(@seoul_forest_record_id, 1, 37.5445810, 127.0373720, '2026-09-25 19:00:00'),
(@seoul_forest_record_id, 2, 37.5449020, 127.0381520, '2026-09-25 19:08:00'),
(@seoul_forest_record_id, 3, 37.5454280, 127.0391240, '2026-09-25 19:16:00'),
(@seoul_forest_record_id, 4, 37.5460210, 127.0402180, '2026-09-25 19:25:00'),
(@seoul_forest_record_id, 5, 37.5465140, 127.0414070, '2026-09-25 19:34:00'),
(@seoul_forest_record_id, 6, 37.5470660, 127.0426450, '2026-09-25 19:48:12');


-- =========================================================
-- 2. 한강공원 뚝섬 산책 코스
-- GPS 경로 있음
-- =========================================================

INSERT INTO walk_records (
    user_id,
    course_id,
    status,
    started_at,
    ended_at,
    duration_seconds,
    distance_m
) VALUES (
    @test_user_id,
    @course_ttukseom,
    'COMPLETED',
    '2026-09-23 18:20:00',
    '2026-09-23 19:24:30',
    3870,
    4300
);

SET @ttukseom_record_id = LAST_INSERT_ID();

INSERT INTO walk_record_points (
    walk_record_id,
    sequence_no,
    latitude,
    longitude,
    recorded_at
) VALUES
(@ttukseom_record_id, 1, 37.5294200, 127.0671900, '2026-09-23 18:20:00'),
(@ttukseom_record_id, 2, 37.5297100, 127.0682100, '2026-09-23 18:31:00'),
(@ttukseom_record_id, 3, 37.5300300, 127.0694200, '2026-09-23 18:42:00'),
(@ttukseom_record_id, 4, 37.5304200, 127.0706100, '2026-09-23 18:53:00'),
(@ttukseom_record_id, 5, 37.5308600, 127.0717800, '2026-09-23 19:05:00'),
(@ttukseom_record_id, 6, 37.5312100, 127.0729200, '2026-09-23 19:24:30');


-- =========================================================
-- 3. 북서울꿈의숲 산책 코스
-- GPS 경로 있음
-- =========================================================

INSERT INTO walk_records (
    user_id,
    course_id,
    status,
    started_at,
    ended_at,
    duration_seconds,
    distance_m
) VALUES (
    @test_user_id,
    @course_dream_forest,
    'COMPLETED',
    '2026-09-20 09:10:00',
    '2026-09-20 09:52:45',
    2565,
    2700
);

SET @dream_forest_record_id = LAST_INSERT_ID();

INSERT INTO walk_record_points (
    walk_record_id,
    sequence_no,
    latitude,
    longitude,
    recorded_at
) VALUES
(@dream_forest_record_id, 1, 37.6208700, 127.0406100, '2026-09-20 09:10:00'),
(@dream_forest_record_id, 2, 37.6211800, 127.0412400, '2026-09-20 09:18:00'),
(@dream_forest_record_id, 3, 37.6215700, 127.0419300, '2026-09-20 09:26:00'),
(@dream_forest_record_id, 4, 37.6219300, 127.0426100, '2026-09-20 09:34:00'),
(@dream_forest_record_id, 5, 37.6222800, 127.0433100, '2026-09-20 09:42:00'),
(@dream_forest_record_id, 6, 37.6226100, 127.0440100, '2026-09-20 09:52:45');


-- =========================================================
-- 4. 자유 산책
-- 추천 코스를 선택하지 않았으므로 course_id는 NULL
-- GPS 경로 있음
-- =========================================================

INSERT INTO walk_records (
    user_id,
    course_id,
    status,
    started_at,
    ended_at,
    duration_seconds,
    distance_m
) VALUES (
    @test_user_id,
    NULL,
    'COMPLETED',
    '2026-09-18 20:00:00',
    '2026-09-18 20:31:20',
    1880,
    1900
);

SET @free_walk_record_id = LAST_INSERT_ID();

INSERT INTO walk_record_points (
    walk_record_id,
    sequence_no,
    latitude,
    longitude,
    recorded_at
) VALUES
(@free_walk_record_id, 1, 37.5662100, 126.9779300, '2026-09-18 20:00:00'),
(@free_walk_record_id, 2, 37.5665500, 126.9785100, '2026-09-18 20:06:00'),
(@free_walk_record_id, 3, 37.5669100, 126.9791200, '2026-09-18 20:12:00'),
(@free_walk_record_id, 4, 37.5672400, 126.9797100, '2026-09-18 20:18:00'),
(@free_walk_record_id, 5, 37.5676100, 126.9803100, '2026-09-18 20:24:00'),
(@free_walk_record_id, 6, 37.5679200, 126.9808900, '2026-09-18 20:31:20');


-- =========================================================
-- 5. 양재천 산책 코스
-- GPS 좌표가 없는 활동 테스트
--
-- 활동 상세 페이지에서
-- "산책 경로 정보가 없습니다."
-- 메시지가 정상적으로 표시되는지 확인
-- =========================================================

INSERT INTO walk_records (
    user_id,
    course_id,
    status,
    started_at,
    ended_at,
    duration_seconds,
    distance_m
) VALUES (
    @test_user_id,
    @course_yangjaecheon,
    'COMPLETED',
    '2026-09-15 17:30:00',
    '2026-09-15 18:26:40',
    3400,
    3800
);

SET @yangjaecheon_record_id = LAST_INSERT_ID();


-- =========================================================
-- 활동 기록 확인
-- 총 5건이 조회되어야 함
-- =========================================================

SELECT
    wr.walk_record_id,
    wr.user_id,
    wr.course_id,
    COALESCE(c.name, '자유 산책') AS course_name,
    wr.status,
    wr.started_at,
    wr.ended_at,
    wr.duration_seconds,
    wr.distance_m
FROM walk_records wr
LEFT JOIN courses c
    ON wr.course_id = c.course_id
WHERE wr.user_id = @test_user_id
ORDER BY wr.started_at DESC;


-- =========================================================
-- GPS 포인트 개수 확인
--
-- 예상 결과
-- 서울숲 반려견 산책 코스 : 6
-- 한강공원 뚝섬 산책 코스 : 6
-- 북서울꿈의숲 산책 코스  : 6
-- 자유 산책                : 6
-- 양재천 산책 코스         : 0
-- =========================================================

SELECT
    wr.walk_record_id,
    COALESCE(c.name, '자유 산책') AS course_name,
    COUNT(wrp.walk_record_point_id) AS point_count
FROM walk_records wr
LEFT JOIN courses c
    ON wr.course_id = c.course_id
LEFT JOIN walk_record_points wrp
    ON wr.walk_record_id = wrp.walk_record_id
WHERE wr.user_id = @test_user_id
GROUP BY wr.walk_record_id, c.name, wr.started_at
ORDER BY wr.started_at DESC;


-- =========================================================
-- GPS 좌표 상세 확인
-- =========================================================

SELECT
    wrp.walk_record_point_id,
    wrp.walk_record_id,
    wrp.sequence_no,
    wrp.latitude,
    wrp.longitude,
    wrp.recorded_at
FROM walk_record_points wrp
JOIN walk_records wr
    ON wrp.walk_record_id = wr.walk_record_id
WHERE wr.user_id = @test_user_id
ORDER BY wrp.walk_record_id, wrp.sequence_no;