USE meongjaguk;

-- =========================================================
-- 마이페이지 동행 모집 / 신청 테스트용 더미데이터
--
-- 목적
-- 1. "내가 공유한 글" 실제 DB 조회 테스트
-- 2. "신청 수락·거절" 실제 DB 조회 테스트
-- 3. 신청 수락 / 거절 POST 처리 테스트
--
-- 전제
-- 1. user_id = 1인 로그인 테스트 회원이 존재해야 함
-- 2. 추천 코스 더미데이터가 먼저 입력되어 있어야 함
-- =========================================================

SET @host_user_id = 1;


-- =========================================================
-- 추천 코스 조회
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


-- =========================================================
-- 신청자 테스트 회원
-- 동일 provider/provider_id가 있으면 닉네임만 갱신
-- =========================================================

INSERT INTO users (
    provider,
    provider_id,
    nickname
) VALUES (
    'test',
    'mypage-applicant-1',
    '김민준'
)
ON DUPLICATE KEY UPDATE
    nickname = '김민준';

INSERT INTO users (
    provider,
    provider_id,
    nickname
) VALUES (
    'test',
    'mypage-applicant-2',
    '정서윤'
)
ON DUPLICATE KEY UPDATE
    nickname = '정서윤';

INSERT INTO users (
    provider,
    provider_id,
    nickname
) VALUES (
    'test',
    'mypage-applicant-3',
    '이현우'
)
ON DUPLICATE KEY UPDATE
    nickname = '이현우';


SET @applicant_1 = (
    SELECT user_id
    FROM users
    WHERE provider = 'test'
      AND provider_id = 'mypage-applicant-1'
);

SET @applicant_2 = (
    SELECT user_id
    FROM users
    WHERE provider = 'test'
      AND provider_id = 'mypage-applicant-2'
);

SET @applicant_3 = (
    SELECT user_id
    FROM users
    WHERE provider = 'test'
      AND provider_id = 'mypage-applicant-3'
);


-- =========================================================
-- 이전에 같은 더미데이터를 실행한 경우 정리
-- =========================================================

DELETE wa
FROM walk_applications wa
JOIN walk_meetings wm
    ON wa.meeting_id = wm.meeting_id
WHERE wm.host_user_id = @host_user_id
  AND wm.title IN (
      '서울숲 저녁 산책 모임',
      '주말 뚝섬 산책 모임'
  );

DELETE FROM walk_meetings
WHERE host_user_id = @host_user_id
  AND title IN (
      '서울숲 저녁 산책 모임',
      '주말 뚝섬 산책 모임'
  );


-- =========================================================
-- 1. 서울숲 저녁 산책 모임
-- =========================================================

INSERT INTO walk_meetings (
    host_user_id,
    course_id,
    title,
    description,
    meeting_date,
    meeting_time,
    max_participants,
    pet_required,
    participation_condition,
    status
) VALUES (
    @host_user_id,
    @course_seoul_forest,
    '서울숲 저녁 산책 모임',
    '퇴근 후 서울숲에서 여유롭게 같이 산책해요.',
    '2026-09-28',
    '19:00:00',
    4,
    TRUE,
    '다른 강아지와 함께 산책 가능한 반려견',
    'RECRUITING'
);

SET @meeting_seoul_forest = LAST_INSERT_ID();


-- =========================================================
-- 2. 주말 뚝섬 산책 모임
-- =========================================================

INSERT INTO walk_meetings (
    host_user_id,
    course_id,
    title,
    description,
    meeting_date,
    meeting_time,
    max_participants,
    pet_required,
    participation_condition,
    status
) VALUES (
    @host_user_id,
    @course_ttukseom,
    '주말 뚝섬 산책 모임',
    '주말 오전 한강을 보면서 천천히 산책할 분을 모집합니다.',
    '2026-10-03',
    '10:30:00',
    5,
    FALSE,
    '반려견 없이 참여해도 괜찮아요.',
    'RECRUITING'
);

SET @meeting_ttukseom = LAST_INSERT_ID();


-- =========================================================
-- 동행 신청
--
-- 서울숲
-- 김민준 : PENDING
-- 정서윤 : ACCEPTED
--
-- 뚝섬
-- 이현우 : PENDING
-- =========================================================

INSERT INTO walk_applications (
    meeting_id,
    user_id,
    status,
    message
) VALUES (
    @meeting_seoul_forest,
    @applicant_1,
    'PENDING',
    '저녁 시간 가능해요. 같이 산책하고 싶습니다.'
);

INSERT INTO walk_applications (
    meeting_id,
    user_id,
    status,
    message
) VALUES (
    @meeting_seoul_forest,
    @applicant_2,
    'ACCEPTED',
    '서울숲 자주 가요. 함께 걷고 싶어요.'
);

INSERT INTO walk_applications (
    meeting_id,
    user_id,
    status,
    message
) VALUES (
    @meeting_ttukseom,
    @applicant_3,
    'PENDING',
    '주말 오전 참여 가능합니다.'
);


-- =========================================================
-- 내가 작성한 모집글 확인
-- =========================================================

SELECT
    wm.meeting_id,
    wm.title,
    c.name AS course_name,
    wm.meeting_date,
    wm.meeting_time,
    wm.max_participants,
    wm.status
FROM walk_meetings wm
JOIN courses c
    ON wm.course_id = c.course_id
WHERE wm.host_user_id = @host_user_id
ORDER BY wm.created_at DESC;


-- =========================================================
-- 신청 목록 확인
-- =========================================================

SELECT
    wa.application_id,
    wm.title AS meeting_title,
    u.nickname AS applicant_nickname,
    wa.status,
    wa.message
FROM walk_applications wa
JOIN walk_meetings wm
    ON wa.meeting_id = wm.meeting_id
JOIN users u
    ON wa.user_id = u.user_id
WHERE wm.host_user_id = @host_user_id
ORDER BY wa.created_at DESC;