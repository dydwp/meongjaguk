package com.meongjaguk.app.integration;

import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.CompanionRequest;
import com.meongjaguk.app.entity.Notification;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.CompanionRequestRepository;
import com.meongjaguk.app.repository.NotificationRepository;
import com.meongjaguk.app.service.CompanionService;
import com.meongjaguk.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 동행 신청·수락이 실제 DB(제약조건, 집계 쿼리, 알림 저장)와 맞게 동작하는지 */
class CompanionIntegrationTest extends IntegrationTestSupport {

    @Autowired CompanionService companionService;
    @Autowired CompanionRequestRepository requests;
    @Autowired NotificationRepository notifications;

    private User host;
    private User applicant;
    private Board board;

    @BeforeEach
    void setUp() {
        host = data.user("용제");
        applicant = data.user("민준");
        board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 3);
    }

    @Test
    void applyStoresPendingRequestAndNotifiesHost() {
        companionService.apply(board.getMeetingId(), applicant.getUserId());
        flushAndClear();

        CompanionRequest saved = requests
                .findByMeetingIdAndApplicant_UserId(board.getMeetingId(), applicant.getUserId()).orElseThrow();
        assertEquals(ApplicationStatus.PENDING, saved.getStatus());
        assertTrue(saved.getCreatedAt() != null);

        List<Notification> hostNotifications = notifications
                .findTop10ByUserIdOrderByCreatedAtDescNotificationIdDesc(host.getUserId());
        assertEquals(1, hostNotifications.size());
        assertEquals(Notification.TYPE_COMPANION_REQUESTED, hostNotifications.get(0).getType());
        assertEquals(board.getMeetingId(), hostNotifications.get(0).getReferenceId());
    }

    @Test
    void secondApplyIsRejected() {
        companionService.apply(board.getMeetingId(), applicant.getUserId());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> companionService.apply(board.getMeetingId(), applicant.getUserId()));
        assertEquals("이미 신청한 모집이에요.", e.getMessage());
    }

    @Test
    void databaseRejectsDuplicateRequest() {
        data.pendingRequest(board, applicant);

        assertThrows(DataIntegrityViolationException.class,
                () -> requests.saveAndFlush(CompanionRequest.create(board, applicant)));
    }

    @Test
    void acceptUpdatesStatusAndNotifiesApplicant() {
        CompanionRequest request = data.pendingRequest(board, applicant);

        companionService.acceptForHost(request.getId(), host.getUserId());
        flushAndClear();

        assertEquals(ApplicationStatus.ACCEPTED, requests.findById(request.getId()).orElseThrow().getStatus());
        Notification notification = notifications
                .findTop10ByUserIdOrderByCreatedAtDescNotificationIdDesc(applicant.getUserId()).get(0);
        assertEquals(Notification.TYPE_COMPANION_ACCEPTED, notification.getType());
        assertEquals("'저녁 산책' 모집에 함께 걷게 됐어요.", notification.getMessage());
    }

    @Test
    void acceptStopsAtCapacity() {
        data.acceptedRequest(board, data.user("서연"));
        data.acceptedRequest(board, data.user("지훈")); // 작성자 + 2명 = 정원 3
        CompanionRequest late = data.pendingRequest(board, applicant);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> companionService.acceptForHost(late.getId(), host.getUserId()));
        assertEquals("모집 정원이 가득 찼습니다.", e.getMessage());
    }

    @Test
    void acceptedCountsAreGroupedPerMeeting() {
        Board other = data.board(host, "아침 산책", LocalDateTime.now().plusDays(2), 5);
        data.acceptedRequest(board, applicant);
        data.acceptedRequest(board, data.user("서연"));
        data.rejectedRequest(board, data.user("지훈"));
        data.acceptedRequest(other, applicant);
        data.pendingRequest(other, data.user("하준"));

        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : requests.countByMeetingIdsAndStatus(
                List.of(board.getMeetingId(), other.getMeetingId()), ApplicationStatus.ACCEPTED)) {
            counts.put((Long) row[0], (Long) row[1]);
        }

        assertEquals(Map.of(board.getMeetingId(), 2L, other.getMeetingId(), 1L), counts);
        assertEquals(2L, requests.countByMeetingIdAndStatus(board.getMeetingId(), ApplicationStatus.ACCEPTED));
    }

    @Test
    void cancelRemovesPendingRequest() {
        data.pendingRequest(board, applicant);

        companionService.cancel(board.getMeetingId(), applicant.getUserId());
        flushAndClear();

        assertTrue(requests.findByMeetingIdAndApplicant_UserId(board.getMeetingId(), applicant.getUserId()).isEmpty());
    }
}
