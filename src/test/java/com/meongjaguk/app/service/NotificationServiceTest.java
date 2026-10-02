package com.meongjaguk.app.service;

import com.meongjaguk.app.dto.NotificationView;
import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.CompanionRequest;
import com.meongjaguk.app.entity.Notification;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static com.meongjaguk.app.support.Fixtures.board;
import static com.meongjaguk.app.support.Fixtures.request;
import static com.meongjaguk.app.support.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private NotificationRepository repository;
    private NotificationService service;

    private final User host = user(1L, "용제");
    private final User applicant = user(2L, "민준");
    private Board board;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        service = new NotificationService(repository);
        board = board(10L, host, LocalDateTime.now().plusDays(1), 4);
        ReflectionTestUtils.setField(board, "title", "저녁 산책");
    }

    @Test
    void companionAcceptedNotifiesApplicant() {
        CompanionRequest accepted = request(5L, board, applicant, ApplicationStatus.ACCEPTED);

        service.notifyCompanionAccepted(accepted);

        Notification saved = savedNotification();
        assertEquals(2L, saved.getUserId());
        assertEquals(Notification.TYPE_COMPANION_ACCEPTED, saved.getType());
        assertEquals(10L, saved.getReferenceId());
        assertEquals("동행 신청이 수락됐어요", saved.getTitle());
        assertEquals("'저녁 산책' 모집에 함께 걷게 됐어요.", saved.getMessage());
    }

    @Test
    void companionRequestedNotifiesHost() {
        service.notifyCompanionRequested(board, applicant);

        Notification saved = savedNotification();
        assertEquals(1L, saved.getUserId());
        assertEquals(Notification.TYPE_COMPANION_REQUESTED, saved.getType());
        assertEquals("민준님이 '저녁 산책'에 동행을 신청했어요.", saved.getMessage());
    }

    @Test
    void commentOnOwnBoardDoesNotNotify() {
        service.notifyCommentAdded(board, host, "제가 쓴 댓글");

        verify(repository, never()).save(any());
    }

    @Test
    void commentNotificationShowsOnlyFirst40Characters() {
        String longComment = "가".repeat(45);

        service.notifyCommentAdded(board, applicant, longComment);

        Notification saved = savedNotification();
        assertEquals(1L, saved.getUserId());
        assertEquals(Notification.TYPE_COMMENT_ADDED, saved.getType());
        assertEquals("민준님: " + "가".repeat(40) + "…", saved.getMessage());
    }

    @Test
    void shortCommentIsShownAsIs() {
        service.notifyCommentAdded(board, applicant, "같이 가요!");

        assertEquals("민준님: 같이 가요!", savedNotification().getMessage());
    }

    @Test
    void recentNotificationsLinkToTheRightPage() {
        Notification requested = withId(Notification.companionRequested(1L, 10L, "민준", "저녁 산책"), 3L);
        Notification accepted = withId(Notification.companionAccepted(1L, 10L, "저녁 산책"), 2L);
        Notification noReference = withId(Notification.companionAccepted(1L, null, "삭제된 모집"), 1L);
        when(repository.findTop10ByUserIdOrderByCreatedAtDescNotificationIdDesc(1L))
                .thenReturn(List.of(requested, accepted, noReference));

        List<NotificationView> views = service.getRecent(1L);

        assertEquals(3, views.size());
        assertEquals("/mypage?tab=requests", views.get(0).link());
        assertEquals("/course-detail-shared?meetingId=10", views.get(1).link());
        assertNull(views.get(2).link());
        assertEquals(3L, views.get(0).notificationId());
    }

    @Test
    void unreadCountAndMarkAllReadUseRepository() {
        when(repository.countByUserIdAndReadFalse(1L)).thenReturn(4L);

        assertEquals(4L, service.countUnread(1L));
        service.markAllRead(1L);

        verify(repository).markAllRead(1L);
    }

    @Test
    void messageLongerThan500IsCut() {
        Notification notification = Notification.companionAccepted(1L, 10L, "제".repeat(600));

        assertEquals(500, notification.getMessage().length());
        assertTrue(notification.getMessage().startsWith("'제"));
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static Notification withId(Notification notification, long id) {
        ReflectionTestUtils.setField(notification, "notificationId", id);
        return notification;
    }
}
