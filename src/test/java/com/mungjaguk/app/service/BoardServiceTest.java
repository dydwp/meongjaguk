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
import com.mungjaguk.app.entity.CoursePoint;
import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.BoardCommentRepository;
import com.mungjaguk.app.repository.BoardRepository;
import com.mungjaguk.app.repository.CompanionRequestRepository;
import com.mungjaguk.app.repository.CoursePointRepository;
import com.mungjaguk.app.repository.RouteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static com.mungjaguk.app.support.Fixtures.board;
import static com.mungjaguk.app.support.Fixtures.comment;
import static com.mungjaguk.app.support.Fixtures.request;
import static com.mungjaguk.app.support.Fixtures.route;
import static com.mungjaguk.app.support.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardServiceTest {

    private BoardRepository boards;
    private CompanionRequestRepository requests;
    private BoardCommentRepository comments;
    private UserService users;
    private RouteRepository routes;
    private CoursePointRepository coursePoints;
    private NotificationService notifications;
    private BoardService service;

    private final User host = user(1L, "용제");
    private final User applicant = user(2L, "민준");
    private final LocalDateTime tomorrow = LocalDateTime.now().plusDays(1).withNano(0);

    @BeforeEach
    void setUp() {
        boards = mock(BoardRepository.class);
        requests = mock(CompanionRequestRepository.class);
        comments = mock(BoardCommentRepository.class);
        users = mock(UserService.class);
        routes = mock(RouteRepository.class);
        coursePoints = mock(CoursePointRepository.class);
        notifications = mock(NotificationService.class);
        service = new BoardService(boards, requests, comments, users, routes, coursePoints, notifications);
        when(users.findById(1L)).thenReturn(host);
        when(users.findById(2L)).thenReturn(applicant);
    }

    @Nested
    class Listing {

        @Test
        void firstPageAsksForOneMoreToKnowIfThereIsNext() {
            Route course = route(3, "한강 코스");
            List<Board> found = new ArrayList<>();
            for (long id = 9; id >= 7; id--) {
                found.add(board(id, host, course, tomorrow, 4));
            }
            when(boards.findByOrderByMeetingIdDesc(PageRequest.of(0, 3))).thenReturn(found);
            when(coursePoints.findByCourse_CourseIdInOrderByCourse_CourseIdAscSequenceNoAsc(any()))
                    .thenReturn(List.of(CoursePoint.create(course, 1, 37.5, 127.0),
                            CoursePoint.create(course, 2, 37.6, 127.1)));

            BoardPageDto page = service.getBoards(null, 2);

            assertTrue(page.hasNext());
            assertEquals(8L, page.nextCursor());
            assertEquals(List.of(9L, 8L), page.items().stream().map(BoardCardDto::meetingId).toList());
            assertEquals(2, page.items().get(0).points().size());
            assertEquals("용제", page.items().get(0).hostNickname());
        }

        @Test
        void nextPageUsesCursor() {
            when(boards.findByMeetingIdLessThanOrderByMeetingIdDesc(8L, PageRequest.of(0, 3)))
                    .thenReturn(List.of(board(7L, host, tomorrow, 4)));

            BoardPageDto page = service.getBoards(8L, 2);

            assertFalse(page.hasNext());
            assertEquals(7L, page.nextCursor());
            verify(boards, never()).findByOrderByMeetingIdDesc(any());
        }

        @Test
        void emptyPage() {
            when(boards.findByOrderByMeetingIdDesc(any())).thenReturn(List.of());

            BoardPageDto page = service.getBoards(null, 6);

            assertTrue(page.items().isEmpty());
            assertFalse(page.hasNext());
            assertNull(page.nextCursor());
        }

        @Test
        void fullOrPastRecruitingBoardsAreShownClosed() {
            Board full = board(3L, host, tomorrow, 2);
            Board past = board(2L, host, LocalDateTime.now().minusMinutes(5), 4);
            Board open = board(1L, host, tomorrow, 4);
            when(boards.findByOrderByMeetingIdDesc(any())).thenReturn(List.of(full, past, open));
            when(requests.countByMeetingIdsAndStatus(List.of(3L, 2L, 1L), ApplicationStatus.ACCEPTED))
                    .thenReturn(List.<Object[]>of(new Object[]{3L, 1L}));

            List<BoardCardDto> items = service.getBoards(null, 6).items();

            assertEquals("CLOSED", items.get(0).status());
            assertEquals(2, items.get(0).currentParticipants());
            assertEquals("CLOSED", items.get(1).status());
            assertEquals("RECRUITING", items.get(2).status());
            assertEquals(1, items.get(2).currentParticipants());
            assertTrue(items.get(2).points().isEmpty());
        }
    }

    @Nested
    class Detail {

        private Board board;

        @BeforeEach
        void givenBoard() {
            board = board(10L, host, tomorrow, 4);
            when(boards.findWithHostAndCourseByMeetingId(10L)).thenReturn(Optional.of(board));
        }

        @Test
        void participantsStartWithHost() {
            User other = user(3L, "서연");
            when(requests.findByMeetingIdAndStatusOrderByCreatedAtAsc(10L, ApplicationStatus.ACCEPTED))
                    .thenReturn(List.of(request(1L, board, applicant, ApplicationStatus.ACCEPTED),
                            request(2L, board, other, ApplicationStatus.ACCEPTED)));
            when(requests.findByMeetingIdAndApplicant_UserId(10L, 2L))
                    .thenReturn(Optional.of(request(1L, board, applicant, ApplicationStatus.ACCEPTED)));

            BoardDetailDto detail = service.getBoard(10L, 2L);

            assertEquals(List.of("용제", "민준", "서연"), detail.participantNicknames());
            assertEquals(3, detail.currentParticipants());
            assertFalse(detail.isHost());
            assertEquals("ACCEPTED", detail.myApplicationStatus());
            assertEquals("서울숲 코스", detail.courseName());
        }

        @Test
        void hostSeesIsHost() {
            assertTrue(service.getBoard(10L, 1L).isHost());
        }

        @Test
        void guestHasNoApplicationStatus() {
            BoardDetailDto detail = service.getBoard(10L, null);

            assertFalse(detail.isHost());
            assertNull(detail.myApplicationStatus());
            verify(requests, never()).findByMeetingIdAndApplicant_UserId(anyLong(), anyLong());
        }

        @Test
        void missingBoardIsNotFound() {
            assertThrows(NoSuchElementException.class, () -> service.getBoard(99L, null));
        }
    }

    @Nested
    class Create {

        private final LocalDate date = tomorrow.toLocalDate();
        private final LocalTime time = LocalTime.of(10, 0);

        private BoardCreateRequest existingCourse(String title, LocalDate date, LocalTime time, Integer max,
                                                  String condition, String description) {
            return new BoardCreateRequest(3, null, title, date, time, max, null, condition, description, null);
        }

        private BoardCreateRequest valid() {
            return existingCourse("  주말 산책  ", date, time, 4, " 소형견 ", null);
        }

        @Test
        void savesBoardWithExistingCourse() {
            Route course = route(3, "한강 코스");
            when(routes.findById(3)).thenReturn(Optional.of(course));
            when(boards.save(any())).thenAnswer(invocation -> {
                Board saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "meetingId", 77L);
                return saved;
            });

            assertEquals(77L, service.createBoard(1L, valid()));

            ArgumentCaptor<Board> captor = ArgumentCaptor.forClass(Board.class);
            verify(boards).save(captor.capture());
            Board saved = captor.getValue();
            assertEquals("주말 산책", saved.getTitle());
            assertEquals("소형견", saved.getParticipationCondition());
            assertFalse(saved.isPetRequired());
            assertTrue(saved.isHostedBy(1L));
            assertEquals(course, saved.getCourse());
            verify(coursePoints, never()).saveAll(any());
            verify(routes, never()).save(any());
        }

        @Test
        void rejectsInvalidInput() {
            assertBadRequest(null, "등록할 내용을 입력해주세요.");
            assertBadRequest(existingCourse("  ", date, time, 4, null, null), "제목을 입력해주세요.");
            assertBadRequest(existingCourse("가".repeat(151), date, time, 4, null, null), "제목은 150자까지 입력할 수 있어요.");
            assertBadRequest(existingCourse("제목", null, time, 4, null, null), "모임 날짜를 선택해주세요.");
            assertBadRequest(existingCourse("제목", date, null, 4, null, null), "모임 시간을 선택해주세요.");
            assertBadRequest(existingCourse("제목", LocalDate.now().minusDays(1), time, 4, null, null), "지난 날짜는 선택할 수 없어요.");
            assertBadRequest(existingCourse("제목", LocalDate.now(), LocalTime.MIN, 4, null, null), "지난 시간은 선택할 수 없어요.");
            assertBadRequest(existingCourse("제목", date, time, null, null, null), "최대 인원을 입력해주세요.");
            assertBadRequest(existingCourse("제목", date, time, 1, null, null), "최대 인원은 2명부터 10명까지 설정할 수 있어요.");
            assertBadRequest(existingCourse("제목", date, time, 11, null, null), "최대 인원은 2명부터 10명까지 설정할 수 있어요.");
            assertBadRequest(existingCourse("제목", date, time, 4, "가".repeat(501), null), "참여 조건은 500자까지 입력할 수 있어요.");
            assertBadRequest(existingCourse("제목", date, time, 4, null, "가".repeat(601)), "설명은 600자까지 입력할 수 있어요.");
            verify(boards, never()).save(any());
        }

        @Test
        void boundaryValuesAreAccepted() {
            when(routes.findById(3)).thenReturn(Optional.of(route(3, "한강 코스")));
            when(boards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.createBoard(1L, existingCourse("가".repeat(150), date, time, 2, "가".repeat(500), "가".repeat(600)));
            service.createBoard(1L, existingCourse("제목", date, time, 10, null, null));
        }

        @Test
        void missingExistingCourseIsNotFound() {
            when(routes.findById(3)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> service.createBoard(1L, valid()));
        }

        @Test
        void newCourseNeedsCourseInfo() {
            BoardCreateRequest noCourse = new BoardCreateRequest(null, null, "제목", date, time, 4, true, null, null, null);

            assertBadRequest(noCourse, "공유할 산책로 정보가 없어요.");
        }

        @Test
        void invalidNewCourseIsRejected() {
            RouteDto zeroDistance = new RouteDto(null, "새 코스", null, 0L, 30, null, null, 37.5, 127.0, null, null);
            RouteDto badLatitude = new RouteDto(null, "새 코스", null, 1000L, 30, null, null, 91.0, 127.0, null, null);

            assertBadRequest(newCourse(zeroDistance, null), "산책로 정보가 올바르지 않아요. 산책로를 다시 추천받아주세요.");
            assertBadRequest(newCourse(badLatitude, null), "산책로 정보가 올바르지 않아요. 산책로를 다시 추천받아주세요.");
        }

        @Test
        void newCourseIsSavedWithPointsRenumberedInOrder() {
            RouteDto dto = new RouteDto(null, " 새 코스 ", "  ", 1800L, 25, "평지", " 성동구 ", 37.5, 127.0, null, null);
            when(routes.save(any())).thenAnswer(invocation -> {
                Route saved = invocation.getArgument(0);
                saved.setCourseId(55);
                return saved;
            });
            when(boards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.createBoard(1L, newCourse(dto, List.of(
                    new CoursePointDto(20, 37.52, 127.02),
                    new CoursePointDto(5, 37.50, 127.00),
                    new CoursePointDto(10, 37.51, 127.01))));

            ArgumentCaptor<Route> route = ArgumentCaptor.forClass(Route.class);
            verify(routes).save(route.capture());
            assertEquals("새 코스", route.getValue().getCourseName());
            assertNull(route.getValue().getDescription());
            assertEquals("성동구", route.getValue().getRegion());

            List<CoursePoint> points = savedCoursePoints();
            assertEquals(List.of(1, 2, 3), points.stream().map(CoursePoint::getSequenceNo).toList());
            assertEquals(List.of(37.50, 37.51, 37.52), points.stream().map(CoursePoint::getLatitude).toList());
        }

        @Test
        void newCourseWithoutPointsSavesOnlyCourse() {
            RouteDto dto = new RouteDto(null, "새 코스", null, 1800L, 25, null, null, 37.5, 127.0, null, null);
            when(routes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
            when(boards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            service.createBoard(1L, newCourse(dto, List.of()));

            verify(coursePoints, never()).saveAll(any());
        }

        @Test
        void invalidPointsAreRejected() {
            RouteDto dto = new RouteDto(null, "새 코스", null, 1800L, 25, null, null, 37.5, 127.0, null, null);
            when(routes.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            assertThrows(IllegalArgumentException.class, () -> service.createBoard(1L,
                    newCourse(dto, List.of(new CoursePointDto(1, 37.5, 127.0)))));
            assertThrows(IllegalArgumentException.class, () -> service.createBoard(1L,
                    newCourse(dto, List.of(new CoursePointDto(1, 37.5, 127.0), new CoursePointDto(2, 37.5, 181.0)))));
            assertThrows(IllegalArgumentException.class, () -> service.createBoard(1L,
                    newCourse(dto, List.of(new CoursePointDto(1, 37.5, 127.0), new CoursePointDto(null, 37.5, 127.0)))));
            verify(boards, never()).save(any());
        }

        private BoardCreateRequest newCourse(RouteDto course, List<CoursePointDto> points) {
            return new BoardCreateRequest(null, course, "새 코스 산책", date, time, 4, true, null, null, points);
        }

        private void assertBadRequest(BoardCreateRequest request, String message) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.createBoard(1L, request));
            assertEquals(message, e.getMessage());
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private List<CoursePoint> savedCoursePoints() {
            ArgumentCaptor<Iterable<CoursePoint>> captor = ArgumentCaptor.forClass((Class) Iterable.class);
            verify(coursePoints).saveAll(captor.capture());
            return StreamSupport.stream(captor.getValue().spliterator(), false).toList();
        }
    }

    @Nested
    class Comments {

        private Board board;

        @BeforeEach
        void givenBoard() {
            board = board(10L, host, tomorrow, 4);
            when(boards.findWithHostAndCourseByMeetingId(10L)).thenReturn(Optional.of(board));
        }

        @Test
        void listMarksHostAndMyComments() {
            when(comments.findByBoard_MeetingIdOrderByCreatedAtDescCommentIdDesc(10L)).thenReturn(List.of(
                    comment(1L, board, host, "환영해요"),
                    comment(2L, board, applicant, "참여할게요")));

            List<CommentDto> list = service.getComments(10L, 2L);

            assertTrue(list.get(0).hostComment());
            assertFalse(list.get(0).mine());
            assertFalse(list.get(1).hostComment());
            assertTrue(list.get(1).mine());
        }

        @Test
        void guestSeesNoMineFlag() {
            when(comments.findByBoard_MeetingIdOrderByCreatedAtDescCommentIdDesc(10L))
                    .thenReturn(List.of(comment(1L, board, host, "환영해요")));

            assertFalse(service.getComments(10L, null).get(0).mine());
        }

        @Test
        void addCommentTrimsSavesAndNotifies() {
            when(comments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

            CommentDto saved = service.addComment(10L, 2L, "  같이 가요  ");

            assertEquals("같이 가요", saved.content());
            assertTrue(saved.mine());
            assertEquals("민준", saved.authorNickname());
            verify(notifications).notifyCommentAdded(board, applicant, "같이 가요");
        }

        @Test
        void emptyOrTooLongCommentIsRejected() {
            assertThrows(IllegalArgumentException.class, () -> service.addComment(10L, 2L, "   "));
            assertThrows(IllegalArgumentException.class, () -> service.addComment(10L, 2L, null));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> service.addComment(10L, 2L, "가".repeat(501)));
            assertEquals("댓글은 500자까지 입력할 수 있어요.", e.getMessage());
            verify(comments, never()).save(any());
        }

        @Test
        void authorCanDeleteOwnComment() {
            BoardComment mine = comment(5L, board, applicant, "삭제할 댓글");
            when(comments.findById(5L)).thenReturn(Optional.of(mine));

            service.deleteComment(10L, 5L, 2L);

            verify(comments).delete(mine);
        }

        @Test
        void othersCannotDeleteComment() {
            when(comments.findById(5L)).thenReturn(Optional.of(comment(5L, board, applicant, "댓글")));

            assertThrows(AccessDeniedException.class, () -> service.deleteComment(10L, 5L, 1L));
            verify(comments, never()).delete(any());
        }

        @Test
        void commentFromAnotherBoardIsNotFound() {
            when(comments.findById(5L)).thenReturn(Optional.of(comment(5L, board, applicant, "댓글")));

            assertThrows(NoSuchElementException.class, () -> service.deleteComment(11L, 5L, 2L));
        }
    }
}
