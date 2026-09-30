package com.mungjaguk.app.integration;

import com.mungjaguk.app.dto.BoardCardDto;
import com.mungjaguk.app.dto.BoardCreateRequest;
import com.mungjaguk.app.dto.BoardDetailDto;
import com.mungjaguk.app.dto.BoardPageDto;
import com.mungjaguk.app.dto.CommentDto;
import com.mungjaguk.app.dto.CoursePointDto;
import com.mungjaguk.app.dto.RouteDto;
import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.BoardStatus;
import com.mungjaguk.app.entity.CoursePoint;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.BoardCommentRepository;
import com.mungjaguk.app.repository.BoardRepository;
import com.mungjaguk.app.repository.CompanionRequestRepository;
import com.mungjaguk.app.repository.CoursePointRepository;
import com.mungjaguk.app.repository.NotificationRepository;
import com.mungjaguk.app.service.BoardCloseScheduler;
import com.mungjaguk.app.service.BoardService;
import com.mungjaguk.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 산책로 게시판: 등록(코스·좌표 함께 저장), 수정·삭제, 무한스크롤, 상세, 댓글 */
class BoardIntegrationTest extends IntegrationTestSupport {

    @Autowired BoardService boardService;
    @Autowired BoardRepository boards;
    @Autowired CoursePointRepository coursePoints;
    @Autowired NotificationRepository notifications;
    @Autowired BoardCloseScheduler closeScheduler;
    @Autowired CompanionRequestRepository requests;
    @Autowired BoardCommentRepository comments;

    private User host;
    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    @BeforeEach
    void setUp() {
        host = data.user("용제");
    }

    @Test
    void recommendedCourseIsSavedWithBoardAndPoints() {
        RouteDto course = new RouteDto(null, "AI 추천 코스", "공원 한 바퀴", 1800L, 25, "평지", "성동구",
                37.544, 127.043, null, null);
        BoardCreateRequest request = new BoardCreateRequest(null, course, "주말 산책", tomorrow, LocalTime.of(10, 0),
                4, true, "소형견", "같이 걸어요", List.of(
                        new CoursePointDto(2, 37.545, 127.044),
                        new CoursePointDto(1, 37.544, 127.043)));

        Long meetingId = boardService.createBoard(host.getUserId(), request);
        flushAndClear();

        Board saved = boards.findWithHostAndCourseByMeetingId(meetingId).orElseThrow();
        assertEquals("주말 산책", saved.getTitle());
        assertEquals("RECRUITING", saved.getStatus().name());
        assertTrue(saved.isPetRequired());
        Route savedCourse = saved.getCourse();
        assertEquals("AI 추천 코스", savedCourse.getCourseName());
        List<CoursePoint> points = coursePoints.findByCourse_CourseIdOrderBySequenceNoAsc(savedCourse.getCourseId());
        assertEquals(List.of(1, 2), points.stream().map(CoursePoint::getSequenceNo).toList());
        assertEquals(37.544, points.get(0).getLatitude());
    }

    @Test
    void existingCourseCanBeShared() {
        Route course = data.route("한강 코스");

        Long meetingId = boardService.createBoard(host.getUserId(), new BoardCreateRequest(course.getCourseId(), null,
                "한강 산책", tomorrow, LocalTime.of(9, 0), 2, null, null, null, null));
        flushAndClear();

        assertEquals(course.getCourseId(),
                boards.findWithHostAndCourseByMeetingId(meetingId).orElseThrow().getCourse().getCourseId());
    }

    @Test
    void hostUpdatesBoardButCourseStaysAndClosedBoardReopens() {
        Board board = data.board(host, "지난 산책", LocalDateTime.now().minusDays(1), 3);
        Integer courseId = board.getCourse().getCourseId();
        closeScheduler.closeExpiredBoards();
        flushAndClear();

        boardService.updateBoard(board.getMeetingId(), host.getUserId(), new BoardCreateRequest(999, null,
                " 새 제목 ", tomorrow, LocalTime.of(18, 30), 5, true, " 소형견 ", null, null));
        flushAndClear();

        Board updated = boards.findWithHostAndCourseByMeetingId(board.getMeetingId()).orElseThrow();
        assertEquals("새 제목", updated.getTitle());
        assertEquals(tomorrow, updated.getMeetingDate());
        assertEquals(LocalTime.of(18, 30), updated.getMeetingTime());
        assertEquals(5, updated.getMaxParticipants());
        assertEquals("소형견", updated.getParticipationCondition());
        assertEquals(BoardStatus.RECRUITING, updated.getStatus()); // 새 일시로 바꾸면 다시 모집 중
        assertEquals(courseId, updated.getCourse().getCourseId()); // courseId는 무시
    }

    @Test
    void updateRejectsOthersAndMaxBelowCurrentParticipants() {
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 4);
        data.acceptedRequest(board, data.user("민준"));
        data.acceptedRequest(board, data.user("서연"));
        User stranger = data.user("남");
        flushAndClear();

        BoardCreateRequest twoPeople = new BoardCreateRequest(null, null, "저녁 산책", tomorrow, LocalTime.of(19, 0),
                2, false, null, null, null);
        assertThrows(AccessDeniedException.class,
                () -> boardService.updateBoard(board.getMeetingId(), stranger.getUserId(), twoPeople));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> boardService.updateBoard(board.getMeetingId(), host.getUserId(), twoPeople));
        assertEquals("최대 인원은 현재 참여 인원(3명)보다 적을 수 없어요.", e.getMessage());
    }

    @Test
    void hostDeletesBoardWithRequestsCommentsAndNotifications() {
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 4);
        User minjun = data.user("민준");
        data.pendingRequest(board, minjun);
        boardService.addComment(board.getMeetingId(), minjun.getUserId(), "참여할게요"); // 작성자에게 알림
        Board other = data.board(host, "다른 산책", LocalDateTime.now().plusDays(2), 4);
        boardService.addComment(other.getMeetingId(), minjun.getUserId(), "여기도요");
        flushAndClear();

        assertThrows(AccessDeniedException.class,
                () -> boardService.deleteBoard(board.getMeetingId(), minjun.getUserId()));

        boardService.deleteBoard(board.getMeetingId(), host.getUserId());
        flushAndClear();

        assertTrue(boards.findById(board.getMeetingId()).isEmpty());
        assertTrue(requests.findByMeetingIdAndApplicant_UserId(board.getMeetingId(), minjun.getUserId()).isEmpty());
        assertTrue(comments.findByBoard_MeetingIdOrderByCreatedAtDescCommentIdDesc(board.getMeetingId()).isEmpty());
        // 다른 글의 댓글·알림은 남음
        assertEquals(1, comments.findByBoard_MeetingIdOrderByCreatedAtDescCommentIdDesc(other.getMeetingId()).size());
        assertEquals(1L, notifications.countByUserIdAndReadFalse(host.getUserId()));
    }

    @Test
    void infiniteScrollWalksThroughAllBoards() {
        Route course = data.route("공용 코스");
        data.coursePoints(course, 37.1, 127.1, 37.2, 127.2);
        List<Long> created = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            created.add(data.board(host, course, "모집 " + i, LocalDateTime.now().plusDays(1), 4).getMeetingId());
        }
        flushAndClear();

        BoardPageDto first = boardService.getBoards(null, 2);
        BoardPageDto second = boardService.getBoards(first.nextCursor(), 2);
        BoardPageDto third = boardService.getBoards(second.nextCursor(), 2);

        List<Long> seen = new ArrayList<>();
        for (BoardPageDto page : List.of(first, second, third)) {
            page.items().stream().map(BoardCardDto::meetingId).forEach(seen::add);
        }
        assertEquals(created.reversed(), seen);
        assertTrue(first.hasNext());
        assertTrue(second.hasNext());
        assertFalse(third.hasNext());
        assertEquals(2, first.items().get(0).points().size());
        assertEquals("용제", first.items().get(0).hostNickname());
    }

    @Test
    void detailCountsAcceptedParticipants() {
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 3);
        User minjun = data.user("민준");
        data.acceptedRequest(board, minjun);
        data.pendingRequest(board, data.user("서연"));
        flushAndClear();

        BoardDetailDto detail = boardService.getBoard(board.getMeetingId(), minjun.getUserId());

        assertEquals(List.of("용제", "민준"), detail.participantNicknames());
        assertEquals(2, detail.currentParticipants());
        assertEquals("ACCEPTED", detail.myApplicationStatus());
        assertEquals("RECRUITING", detail.status());
    }

    @Test
    void commentsAreOrderedAndNotifyHost() {
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 3);
        User minjun = data.user("민준");

        boardService.addComment(board.getMeetingId(), host.getUserId(), "환영해요");
        CommentDto second = boardService.addComment(board.getMeetingId(), minjun.getUserId(), "참여할게요");
        flushAndClear();

        List<CommentDto> comments = boardService.getComments(board.getMeetingId(), minjun.getUserId());
        // 최신순
        assertEquals(List.of("참여할게요", "환영해요"), comments.stream().map(CommentDto::content).toList());
        assertTrue(comments.get(0).mine());
        assertTrue(comments.get(1).hostComment());

        // 작성자 본인 댓글은 알림 없음, 민준 댓글만 알림
        assertEquals(1L, notifications.countByUserIdAndReadFalse(host.getUserId()));

        boardService.deleteComment(board.getMeetingId(), second.commentId(), minjun.getUserId());
        flushAndClear();
        assertEquals(1, boardService.getComments(board.getMeetingId(), null).size());
    }

    @Test
    void schedulerClosesOnlyExpiredRecruitingBoards() {
        Board past = data.board(host, "어제 산책", LocalDateTime.now().minusDays(1), 3);
        Board justStarted = data.board(host, "방금 시작", LocalDateTime.now().minusMinutes(1), 3);
        Board future = data.board(host, "내일 산책", LocalDateTime.now().plusDays(1), 3);
        flushAndClear();

        closeScheduler.closeExpiredBoards();
        flushAndClear();

        assertEquals(BoardStatus.CLOSED, boards.findById(past.getMeetingId()).orElseThrow().getStatus());
        assertEquals(BoardStatus.CLOSED, boards.findById(justStarted.getMeetingId()).orElseThrow().getStatus());
        assertEquals(BoardStatus.RECRUITING, boards.findById(future.getMeetingId()).orElseThrow().getStatus());
    }

    @Test
    void pastMeetingIsShownClosed() {
        data.board(host, "지난 산책", LocalDateTime.now().minusDays(1), 3);
        flushAndClear();

        BoardCardDto card = boardService.getBoards(null, 6).items().get(0);
        assertEquals("CLOSED", card.status());
        assertNull(boardService.getBoard(card.meetingId(), null).myApplicationStatus());
    }
}
