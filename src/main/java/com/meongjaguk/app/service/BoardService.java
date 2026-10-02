package com.meongjaguk.app.service;

import com.meongjaguk.app.dto.BoardCardDto;
import com.meongjaguk.app.dto.BoardCreateRequest;
import com.meongjaguk.app.dto.BoardDetailDto;
import com.meongjaguk.app.dto.BoardPageDto;
import com.meongjaguk.app.dto.CommentDto;
import com.meongjaguk.app.dto.CoursePointDto;
import com.meongjaguk.app.dto.PetCardView;
import com.meongjaguk.app.dto.RouteDto;
import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.BoardComment;
import com.meongjaguk.app.entity.BoardStatus;
import com.meongjaguk.app.entity.CompanionRequest;
import com.meongjaguk.app.entity.CoursePoint;
import com.meongjaguk.app.entity.Pet;
import com.meongjaguk.app.entity.Route;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.entity.WalkRecord;
import com.meongjaguk.app.repository.BoardCommentRepository;
import com.meongjaguk.app.repository.BoardRepository;
import com.meongjaguk.app.repository.CompanionRequestRepository;
import com.meongjaguk.app.repository.CoursePointRepository;
import com.meongjaguk.app.repository.PetRepository;
import com.meongjaguk.app.repository.RouteRepository;
import com.meongjaguk.app.repository.WalkMeetingPetRepository;
import com.meongjaguk.app.repository.WalkRecordRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 동행 게시판: 목록/상세 조회, 게시글 등록, 댓글 조회/작성/삭제
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final CompanionRequestRepository companionRequestRepository;
    private final BoardCommentRepository commentRepository;
    private final UserService userService;
    private final RouteRepository routeRepository;
    private final CoursePointRepository coursePointRepository;
    private final NotificationService notificationService;
    private final WalkMeetingPetRepository walkMeetingPetRepository;
    private final PetRepository petRepository;
    private final PetService petService;
    private final WalkRecordRepository walkRecordRepository; // 동행 산책에 연결된 기록 (추가: 김환중)

    /** 저장할 수 있는 경로 좌표 최대 개수 (비정상적으로 큰 요청 방지) */
    private static final int MAX_COURSE_POINTS = 5000;

    public BoardService(BoardRepository boardRepository,
            CompanionRequestRepository companionRequestRepository,
            BoardCommentRepository commentRepository,
            UserService userService,
            RouteRepository routeRepository,
            CoursePointRepository coursePointRepository,
            NotificationService notificationService,
            WalkMeetingPetRepository walkMeetingPetRepository,
            PetRepository petRepository,
            PetService petService,
            WalkRecordRepository walkRecordRepository) {
        this.boardRepository = boardRepository;
        this.companionRequestRepository = companionRequestRepository;
        this.commentRepository = commentRepository;
        this.userService = userService;
        this.routeRepository = routeRepository;
        this.coursePointRepository = coursePointRepository;
        this.notificationService = notificationService;
        this.walkMeetingPetRepository = walkMeetingPetRepository;
        this.petRepository = petRepository;
        this.petService = petService;
        this.walkRecordRepository = walkRecordRepository;
    }

    /**
     * 동행 게시판 목록 (무한스크롤)
     * 동행 게시판r가 없으면 첫 페이지, 있으면 그 게시글 번호보다 오래된 글
     * - size개 + 1개를 조회해서 다음 페이지가 있는지 판단
     */
    public BoardPageDto getBoards(Long cursor, int size) {
        PageRequest limit = PageRequest.of(0, size + 1);
        List<Board> boards = (cursor == null)
                ? boardRepository.findByOrderByMeetingIdDesc(limit)
                : boardRepository.findByMeetingIdLessThanOrderByMeetingIdDesc(cursor, limit);

        boolean hasNext = boards.size() > size;
        if (hasNext) {
            boards = boards.subList(0, size);
        }

        List<BoardCardDto> items = toCards(boards);
        Long nextCursor = boards.isEmpty() ? null : boards.get(boards.size() - 1).getMeetingId();
        return new BoardPageDto(items, hasNext, nextCursor);
    }

    /** 게시글 목록 → 카드 (수락 인원은 한 번에 집계) */
    private List<BoardCardDto> toCards(List<Board> boards) {
        if (boards.isEmpty()) {
            return List.of();
        }

        List<Long> meetingIds = new ArrayList<>();
        for (Board board : boards) {
            meetingIds.add(board.getMeetingId());
        }
        Map<Long, Long> acceptedCounts = new HashMap<>();
        for (Object[] row : companionRequestRepository.countByMeetingIdsAndStatus(meetingIds,
                ApplicationStatus.ACCEPTED)) {
            acceptedCounts.put((Long) row[0], (Long) row[1]);
        }

        List<Integer> courseIds = new ArrayList<>();
        for (Board board : boards) {
            courseIds.add(board.getCourse().getCourseId());
        }
        Map<Integer, List<CoursePointDto>> pointsByCourse = new HashMap<>();
        for (CoursePoint point : coursePointRepository
                .findByCourse_CourseIdInOrderByCourse_CourseIdAscSequenceNoAsc(courseIds)) {
            pointsByCourse.computeIfAbsent(point.getCourse().getCourseId(), id -> new ArrayList<>())
                    .add(toPointDto(point));
        }

        List<BoardCardDto> cards = new ArrayList<>();
        for (Board board : boards) {
            Route course = board.getCourse();
            long accepted = acceptedCounts.getOrDefault(board.getMeetingId(), 0L);
            cards.add(new BoardCardDto(
                    board.getMeetingId(),
                    board.getTitle(),
                    board.getHost().getNickname(),
                    board.getCreatedAt(),
                    board.getMeetingDate(),
                    board.getMeetingTime(),
                    course.getDistanceM(),
                    course.getEstimatedMinutes(),
                    1 + (int) accepted,
                    board.getMaxParticipants(),
                    displayStatus(board, accepted),
                    course.getStartLatitude(),
                    course.getStartLongitude(),
                    pointsByCourse.getOrDefault(course.getCourseId(), List.of())));
        }
        return cards;
    }

    /** 공유 산책로 상세 (loginUserId는 비로그인이면 null) */
    public BoardDetailDto getBoard(Long meetingId, Long loginUserId) {
        Board board = findBoard(meetingId);
        Route course = board.getCourse();

        List<CompanionRequest> accepted = companionRequestRepository
                .findByMeetingIdAndStatusOrderByCreatedAtAsc(meetingId, ApplicationStatus.ACCEPTED);

        List<String> participants = new ArrayList<>();
        participants.add(board.getHost().getNickname());
        accepted.forEach(request -> participants.add(request.getApplicant().getNickname()));

        boolean isHost = loginUserId != null && board.isHostedBy(loginUserId);
        String myStatus = null;
        if (loginUserId != null) {
            myStatus = companionRequestRepository.findByMeetingIdAndApplicant_UserId(meetingId, loginUserId)
                    .map(request -> request.getStatus().name())
                    .orElse(null);
        }

        // 로그인 사용자가 아닌 글 작성자의 userId 기준
        Long hostUserId = board.getHost().getUserId();
        List<Long> petIds = walkMeetingPetRepository.findPetIdsByMeetingId(meetingId, hostUserId);
        List<PetCardView> pets = petService.getMyPetsByIds(hostUserId, petIds);

        return new BoardDetailDto(
                board.getMeetingId(),
                board.getTitle(),
                board.getDescription(),
                course.getCourseName(),
                course.getDistanceM(),
                course.getEstimatedMinutes(),
                board.getMeetingDate(),
                board.getMeetingTime(),
                board.isPetRequired(),
                board.getParticipationCondition(),
                board.getHost().getNickname(),
                board.getCreatedAt(),
                participants,
                participants.size(),
                board.getMaxParticipants(),
                displayStatus(board, accepted.size()),
                isHost,
                myStatus,
                course.getStartLatitude(),
                course.getStartLongitude(),
                coursePointRepository.findByCourse_CourseIdOrderBySequenceNoAsc(course.getCourseId())
                        .stream().map(BoardService::toPointDto).toList(),
                pets,
                board.getStartedAt(),
                board.getEndedAt(),
                walkRecordRepository.findByMeetingId(meetingId).map(WalkRecord::getWalkRecordId).orElse(null));
    }

    /**
     * 산책로 게시글(동행 모집) 등록 (로그인 회원만)
     * - courseId가 있으면 기존 코스를 사용, 없으면 추천 코스(course)를 게시글과 함께 저장
     * - 코스와 게시글은 한 트랜잭션으로 저장되어 등록 실패 시 코스도 남지 않음
     * - 제목 1~150자, 모임 일시는 현재 이후, 최대 인원 2~10명(본인 포함),
     * 참여 조건 500자 이하, 설명 600자 이하
     * - 새 코스면 경로 좌표(points)도 course_points에 함께 저장 (추가: 김환중)
     */
    @Transactional
    public Long createBoard(Long userId, BoardCreateRequest request) {
        BoardFields fields = validateFields(request);

        User host = userService.findById(userId);
        Route course = resolveCourse(request);
        if (request.courseId() == null) {
            saveCoursePoints(course, request.points());
        }

        // 모집자가 함께 산책할 반려견 검증 (담당: 최주영)
        List<Long> petIds = validatePetIds(userId, request.petIds());

        Board saved = boardRepository.save(Board.create(host, course, fields.title(), fields.description(),
                fields.meetingDate(), fields.meetingTime(), fields.maxParticipants(),
                fields.petRequired(), fields.condition()));

        walkMeetingPetRepository.savePetLinks(saved.getMeetingId(), petIds);

        return saved.getMeetingId();
    }

    /**
     * 산책로 게시글 수정 (작성자만)
     * - 코스는 바꿀 수 없고 제목·일시·인원·조건·설명·함께할 반려견만 수정 (courseId/course/points는 무시)
     * - 검증 기준은 등록과 같고, 최대 인원은 현재 참여 인원(작성자 + 수락된 신청자)보다 적을 수 없음
     */
    @Transactional
    public void updateBoard(Long meetingId, Long userId, BoardCreateRequest request) {
        Board board = findBoard(meetingId);
        if (!board.isHostedBy(userId)) {
            throw new AccessDeniedException("본인이 작성한 글만 수정할 수 있어요.");
        }
        // 동행 산책을 시작한 글은 수정 불가 (추가: 김환중)
        if (board.isWalkStarted()) {
            throw new IllegalArgumentException("동행 산책을 시작한 글은 수정할 수 없어요.");
        }

        BoardFields fields = validateFields(request);
        long accepted = companionRequestRepository.countByMeetingIdAndStatus(meetingId, ApplicationStatus.ACCEPTED);
        if (fields.maxParticipants() < 1 + accepted) {
            throw new IllegalArgumentException("최대 인원은 현재 참여 인원(" + (1 + accepted) + "명)보다 적을 수 없어요.");
        }

        List<Long> petIds = validatePetIds(userId, request.petIds());

        board.update(fields.title(), fields.description(), fields.meetingDate(), fields.meetingTime(),
                fields.maxParticipants(), fields.petRequired(), fields.condition());

        // 함께할 반려견은 선택한 목록으로 교체
        walkMeetingPetRepository.deletePetLinks(meetingId);
        walkMeetingPetRepository.savePetLinks(meetingId, petIds);
    }

    /**
     * 산책로 게시글 삭제 (작성자만)
     * - 동행 신청, 댓글, 함께할 반려견 연결, 이 글에 대한 알림을 먼저 지우고 게시글 삭제 (코스는 다른 곳에서 쓸 수 있어 남김)
     */
    @Transactional
    public void deleteBoard(Long meetingId, Long userId) {
        Board board = findBoard(meetingId);
        if (!board.isHostedBy(userId)) {
            throw new AccessDeniedException("본인이 작성한 글만 삭제할 수 있어요.");
        }
        // 동행 산책을 시작한 글은 삭제 불가 (추가: 김환중)
        if (board.isWalkStarted()) {
            throw new IllegalArgumentException("동행 산책을 시작한 글은 삭제할 수 없어요.");
        }

        companionRequestRepository.deleteByMeetingId(meetingId);
        commentRepository.deleteByMeetingId(meetingId);
        walkMeetingPetRepository.deletePetLinks(meetingId);
        notificationService.deleteByMeeting(meetingId);
        boardRepository.delete(board);
    }

    /**
     * 동행 산책 시작 (작성자만) (추가: 김환중)
     * - 화면에 보이는 상태가 모집 마감(CLOSED)이고 수락된 참가자가 1명 이상일 때만 가능
     * - 시작하면 산책 중(IN_PROGRESS), 시작 시각 기록
     */
    @Transactional
    public void startWalk(Long meetingId, Long userId) {
        Board board = findBoard(meetingId);
        if (!board.isHostedBy(userId)) {
            throw new AccessDeniedException("본인이 작성한 모집만 산책을 시작할 수 있어요.");
        }
        if (board.isWalkStarted()) {
            throw new IllegalArgumentException("이미 시작한 동행 산책이에요.");
        }

        long accepted = companionRequestRepository.countByMeetingIdAndStatus(meetingId, ApplicationStatus.ACCEPTED);
        if (!BoardStatus.CLOSED.name().equals(displayStatus(board, accepted))) {
            throw new IllegalArgumentException("모집이 마감된 뒤에 산책을 시작할 수 있어요.");
        }
        if (accepted == 0) {
            throw new IllegalArgumentException("수락된 참가자가 있어야 산책을 시작할 수 있어요.");
        }

        board.startWalk(LocalDateTime.now());
    }

    /**
     * 동행 산책 종료 (작성자만, 산책 중일 때만) (추가: 김환중)
     * - 종료하면 완료(COMPLETED), 종료 시각 기록
     */
    @Transactional
    public void completeWalk(Long meetingId, Long userId) {
        Board board = findBoard(meetingId);
        if (!board.isHostedBy(userId)) {
            throw new AccessDeniedException("본인이 작성한 모집만 산책을 종료할 수 있어요.");
        }
        if (board.getStatus() != BoardStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("산책 중인 동행 산책만 종료할 수 있어요.");
        }

        board.completeWalk(LocalDateTime.now());
    }

    /** 등록·수정 공통 입력값 (검증 후 앞뒤 공백 제거된 값) */
    private record BoardFields(String title, String description, LocalDate meetingDate, LocalTime meetingTime,
            int maxParticipants, boolean petRequired, String condition) {
    }

    /**
     * 등록·수정 공통 검증
     * - 제목 1~150자, 모임 일시는 현재 이후, 최대 인원 2~10명(본인 포함),
     * 참여 조건 500자 이하, 설명 600자 이하
     */
    private BoardFields validateFields(BoardCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("등록할 내용을 입력해주세요.");
        }

        String title = trimToNull(request.title());
        if (title == null) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        if (title.length() > Board.MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 " + Board.MAX_TITLE_LENGTH + "자까지 입력할 수 있어요.");
        }

        LocalDate meetingDate = request.meetingDate();
        LocalTime meetingTime = request.meetingTime();
        if (meetingDate == null) {
            throw new IllegalArgumentException("모임 날짜를 선택해주세요.");
        }
        if (meetingTime == null) {
            throw new IllegalArgumentException("모임 시간을 선택해주세요.");
        }
        if (meetingDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("지난 날짜는 선택할 수 없어요.");
        }
        if (!LocalDateTime.of(meetingDate, meetingTime).isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("지난 시간은 선택할 수 없어요.");
        }

        Integer maxParticipants = request.maxParticipants();
        if (maxParticipants == null) {
            throw new IllegalArgumentException("최대 인원을 입력해주세요.");
        }
        if (maxParticipants < Board.MIN_PARTICIPANTS || maxParticipants > Board.MAX_PARTICIPANTS) {
            throw new IllegalArgumentException("최대 인원은 " + Board.MIN_PARTICIPANTS + "명부터 "
                    + Board.MAX_PARTICIPANTS + "명까지 설정할 수 있어요.");
        }

        String condition = trimToNull(request.participationCondition());
        if (condition != null && condition.length() > Board.MAX_CONDITION_LENGTH) {
            throw new IllegalArgumentException("참여 조건은 " + Board.MAX_CONDITION_LENGTH + "자까지 입력할 수 있어요.");
        }
        String description = trimToNull(request.description());
        if (description != null && description.length() > Board.MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("설명은 " + Board.MAX_DESCRIPTION_LENGTH + "자까지 입력할 수 있어요.");
        }

        return new BoardFields(title, description, meetingDate, meetingTime, maxParticipants,
                Boolean.TRUE.equals(request.petRequired()), condition);
    }

    /** 댓글 목록: 최신순 (loginUserId는 비로그인이면 null) */
    public List<CommentDto> getComments(Long meetingId, Long loginUserId) {
        Board board = findBoard(meetingId);
        return commentRepository.findByBoard_MeetingIdOrderByCreatedAtDescCommentIdDesc(meetingId)
                .stream()
                .map(comment -> toCommentDto(comment, board, loginUserId))
                .toList();
    }

    /**
     * 댓글 작성 (로그인 회원만)
     * - 내용은 앞뒤 공백 제거 후 1자 이상, 500자 이하
     */
    @Transactional
    public CommentDto addComment(Long meetingId, Long userId, String content) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요.");
        }
        if (trimmed.length() > BoardComment.MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + BoardComment.MAX_CONTENT_LENGTH + "자까지 입력할 수 있어요.");
        }

        Board board = findBoard(meetingId);
        User user = userService.findById(userId);
        BoardComment saved = commentRepository.save(BoardComment.create(board, user, trimmed));
        notificationService.notifyCommentAdded(board, user, trimmed); // 추가(박용제): 모집 작성자에게 알림
        return toCommentDto(saved, board, userId);
    }

    /**
     * 댓글 삭제
     * - 본인이 작성한 댓글만 삭제 가능
     */
    @Transactional
    public void deleteComment(Long meetingId, Long commentId, Long userId) {
        BoardComment comment = commentRepository.findById(commentId)
                .filter(c -> c.getBoard().getMeetingId().equals(meetingId))
                .orElseThrow(() -> new NoSuchElementException("댓글을 찾을 수 없어요."));

        if (!comment.getUser().getUserId().equals(userId)) {
            throw new AccessDeniedException("본인이 작성한 댓글만 삭제할 수 있어요.");
        }

        commentRepository.delete(comment);
    }

    private CommentDto toCommentDto(BoardComment comment, Board board, Long loginUserId) {
        User author = comment.getUser();
        return new CommentDto(
                comment.getCommentId(),
                author.getNickname(),
                board.isHostedBy(author.getUserId()),
                author.getUserId().equals(loginUserId),
                comment.getContent(),
                comment.getCreatedAt());
    }

    /**
     * 화면 표시용 모집 상태
     * - DB가 모집 중(RECRUITING)이어도 아래 경우는 모집 마감(CLOSED)으로 표시
     * · 정원이 찼을 때 (DB를 CLOSED로 바꾸는 자동 마감은 수락 기능에서 Board.close()로 처리)
     * · 모임 일시가 지났을 때
     */
    private String displayStatus(Board board, long acceptedCount) {
        if (board.getStatus() == BoardStatus.RECRUITING
                && (board.isFull(acceptedCount) || board.isMeetingTimePassed(LocalDateTime.now()))) {
            return BoardStatus.CLOSED.name();
        }
        return board.getStatus().name();
    }

    /** 게시글에 연결할 코스: 기존 코스 조회 또는 추천 코스 새로 저장 */
    private Route resolveCourse(BoardCreateRequest request) {
        if (request.courseId() != null) {
            return routeRepository.findById(request.courseId())
                    .orElseThrow(() -> new NoSuchElementException("산책로를 찾을 수 없어요."));
        }

        RouteDto dto = request.course();
        if (dto == null) {
            throw new IllegalArgumentException("공유할 산책로 정보가 없어요.");
        }
        String name = trimToNull(dto.name());
        if (name == null || name.length() > 100
                || dto.distanceM() == null || dto.distanceM() <= 0
                || dto.estimatedMinutes() == null || dto.estimatedMinutes() <= 0
                || dto.startLatitude() == null || Math.abs(dto.startLatitude()) > 90
                || dto.startLongitude() == null || Math.abs(dto.startLongitude()) > 180
                || (dto.feature() != null && dto.feature().length() > 500)
                || (dto.region() != null && dto.region().length() > 100)) {
            throw new IllegalArgumentException("산책로 정보가 올바르지 않아요. 산책로를 다시 추천받아주세요.");
        }

        Route route = new Route();
        route.setCourseName(name);
        route.setDescription(trimToNull(dto.description()));
        route.setDistanceM(dto.distanceM());
        route.setEstimatedMinutes(dto.estimatedMinutes());
        route.setFeature(dto.feature());
        route.setRegion(trimToNull(dto.region()));
        route.setStartLatitude(dto.startLatitude());
        route.setStartLongitude(dto.startLongitude());
        route.setThumbnailImg(null);
        route.setCreatedAt(LocalDateTime.now());
        return routeRepository.save(route);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 새 코스의 경로 좌표 저장 (추가: 김환중)
     * - 좌표가 없으면 저장하지 않음 (게시판 지도는 출발 지점만 표시)
     * - 좌표가 있으면 2개 이상, 위도·경도 범위 확인 후 sequence 순서대로 1부터 번호를 다시 매겨 저장
     */
    private void saveCoursePoints(Route course, List<CoursePointDto> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        if (points.size() < 2 || points.size() > MAX_COURSE_POINTS) {
            throw new IllegalArgumentException("산책로 정보가 올바르지 않아요. 산책로를 다시 추천받아주세요.");
        }
        for (CoursePointDto p : points) {
            if (p == null || p.sequence() == null || p.latitude() == null || p.longitude() == null
                    || !Double.isFinite(p.latitude()) || !Double.isFinite(p.longitude())
                    || Math.abs(p.latitude()) > 90 || Math.abs(p.longitude()) > 180) {
                throw new IllegalArgumentException("산책로 정보가 올바르지 않아요. 산책로를 다시 추천받아주세요.");
            }
        }

        List<CoursePointDto> sorted = new ArrayList<>(points);
        sorted.sort(Comparator.comparing(CoursePointDto::sequence));
        List<CoursePoint> entities = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            CoursePointDto p = sorted.get(i);
            entities.add(CoursePoint.create(course, i + 1, p.latitude(), p.longitude()));
        }
        coursePointRepository.saveAll(entities);
    }

    private static CoursePointDto toPointDto(CoursePoint point) {
        return new CoursePointDto(point.getSequenceNo(), point.getLatitude(), point.getLongitude());
    }

    private Board findBoard(Long meetingId) {
        return boardRepository.findWithHostAndCourseByMeetingId(meetingId)
                .orElseThrow(() -> new NoSuchElementException("모집 정보를 찾을 수 없어요."));
    }

    /* 모집 글에 연결할 반려견 소유권 검증 (담당: 최주영) */
    private List<Long> validatePetIds(Long userId, List<Long> petIds) {
        if (petIds == null || petIds.isEmpty())
            return List.of();

        if (petIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("반려견 정보가 올바르지 않습니다.");
        }

        List<Long> distinctPetIds = new LinkedHashSet<>(petIds).stream().toList();

        Set<Long> myPetIds = petRepository.findByUser_UserIdOrderByPetIdAsc(userId).stream()
                .map(Pet::getPetId)
                .collect(Collectors.toSet());

        if (!myPetIds.containsAll(distinctPetIds)) {
            throw new IllegalArgumentException("본인의 반려견만 선택할 수 있습니다.");
        }

        return distinctPetIds;
    }
}
