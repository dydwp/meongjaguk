package com.mungjaguk.app.service;

import com.mungjaguk.app.dto.BoardCardDto;
import com.mungjaguk.app.dto.BoardCreateRequest;
import com.mungjaguk.app.dto.BoardDetailDto;
import com.mungjaguk.app.dto.BoardPageDto;
import com.mungjaguk.app.dto.CommentDto;
import com.mungjaguk.app.dto.CoursePointDto;
import com.mungjaguk.app.dto.RouteDto;
import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.BoardComment;
import com.mungjaguk.app.entity.BoardStatus;
import com.mungjaguk.app.entity.CompanionRequest;
import com.mungjaguk.app.entity.CoursePoint;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.BoardCommentRepository;
import com.mungjaguk.app.repository.BoardRepository;
import com.mungjaguk.app.repository.CompanionRequestRepository;
import com.mungjaguk.app.repository.CoursePointRepository;
import com.mungjaguk.app.repository.RouteRepository;
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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 산책로 게시판: 목록/상세 조회, 게시글 등록, 댓글 조회/작성/삭제
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

    /** 저장할 수 있는 경로 좌표 최대 개수 (비정상적으로 큰 요청 방지) */
    private static final int MAX_COURSE_POINTS = 5000;

    public BoardService(BoardRepository boardRepository,
                        CompanionRequestRepository companionRequestRepository,
                        BoardCommentRepository commentRepository,
                        UserService userService,
                        RouteRepository routeRepository,
                        CoursePointRepository coursePointRepository) {
        this.boardRepository = boardRepository;
        this.companionRequestRepository = companionRequestRepository;
        this.commentRepository = commentRepository;
        this.userService = userService;
        this.routeRepository = routeRepository;
        this.coursePointRepository = coursePointRepository;
    }

    /**
     * 산책로 게시판 목록 (무한스크롤)
     * - cursor가 없으면 첫 페이지, 있으면 그 게시글 번호보다 오래된 글
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
        for (Object[] row : companionRequestRepository.countByMeetingIdsAndStatus(meetingIds, ApplicationStatus.ACCEPTED)) {
            acceptedCounts.put((Long) row[0], (Long) row[1]);
        }

        List<Integer> courseIds = new ArrayList<>();
        for (Board board : boards) {
            courseIds.add(board.getCourse().getCourseId());
        }
        Map<Integer, List<CoursePointDto>> pointsByCourse = new HashMap<>();
        for (CoursePoint point : coursePointRepository.findByCourse_CourseIdInOrderByCourse_CourseIdAscSequenceNoAsc(courseIds)) {
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
                        .stream().map(BoardService::toPointDto).toList());
    }

    /**
     * 산책로 게시글(동행 모집) 등록 (로그인 회원만)
     * - courseId가 있으면 기존 코스를 사용, 없으면 추천 코스(course)를 게시글과 함께 저장
     * - 코스와 게시글은 한 트랜잭션으로 저장되어 등록 실패 시 코스도 남지 않음
     * - 제목 1~150자, 모임 일시는 현재 이후, 최대 인원 2~10명(본인 포함),
     *   참여 조건 500자 이하, 설명 600자 이하
     * - 새 코스면 경로 좌표(points)도 course_points에 함께 저장 (추가: 김환중)
     */
    @Transactional
    public Long createBoard(Long userId, BoardCreateRequest request) {
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

        User host = userService.findById(userId);
        Route course = resolveCourse(request);
        if (request.courseId() == null) {
            saveCoursePoints(course, request.points());
        }
        Board saved = boardRepository.save(Board.create(host, course, title, description,
                meetingDate, meetingTime, maxParticipants,
                Boolean.TRUE.equals(request.petRequired()), condition));
        return saved.getMeetingId();
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
     *   · 정원이 찼을 때 (DB를 CLOSED로 바꾸는 자동 마감은 수락 기능에서 Board.close()로 처리)
     *   · 모임 일시가 지났을 때
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
}
