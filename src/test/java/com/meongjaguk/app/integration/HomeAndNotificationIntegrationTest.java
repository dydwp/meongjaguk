package com.meongjaguk.app.integration;

import com.meongjaguk.app.dto.MeetCardDto;
import com.meongjaguk.app.dto.NotificationView;
import com.meongjaguk.app.dto.WeeklyWalkSummary;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.Notification;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.NotificationRepository;
import com.meongjaguk.app.service.MainService;
import com.meongjaguk.app.service.NotificationService;
import com.meongjaguk.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 메인 화면 데이터(MainService)와 알림(NotificationService)을 실제 DB 쿼리로 */
class HomeAndNotificationIntegrationTest extends IntegrationTestSupport {

    @Autowired MainService mainService;
    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notifications;

    private User me;

    @BeforeEach
    void setUp() {
        me = data.user("용제");
    }

    // ---------- 이번 주 나의 산책 ----------

    @Test
    void weeklySummaryIgnoresLastWeekAndOtherUsers() {
        LocalDateTime monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime today = LocalDate.now().atStartOfDay();
        data.walk(me, monday, 600, 1000);                   // 이번 주 월요일 0시 (포함)
        data.walk(me, today, 1200, 2000);                   // 오늘 0시
        data.walk(me, monday.minusSeconds(1), 3000, 5000);   // 지난주 (제외)
        data.walk(data.user("남"), today, 999, 9999);        // 다른 회원 (제외)
        flushAndClear();

        WeeklyWalkSummary summary = mainService.getWeeklyWalkSummary(me.getUserId());

        assertEquals(2, summary.walkCount());
        assertEquals(3.0, summary.distanceKm(), 0.0001);
        assertEquals(30, summary.minutes());
        assertEquals("오늘 2.0km", summary.lastWalkLabel());
    }

    @Test
    void greetingUsesFirstRegisteredPet() {
        data.pet(me, "밤");
        data.pet(me, "보리");
        flushAndClear();

        assertEquals("밤이랑", mainService.getPetWith(me.getUserId()));
    }

    // ---------- 같이 걷기 모집 카드 ----------

    @Test
    void recentMeetsShowRecruitingFirstWithRealCounts() {
        Board open = data.board(me, "모집 중", LocalDateTime.now().plusDays(1), 3);
        Board full = data.board(me, "정원 참", LocalDateTime.now().plusDays(1), 2);
        data.acceptedRequest(full, data.user("민준"));
        data.acceptedRequest(open, data.user("서연"));
        Board closed = data.board(me, "마감", LocalDateTime.now().plusDays(1), 4);
        closed.close();
        flushAndClear();

        List<MeetCardDto> cards = mainService.getRecentMeets(3);

        assertEquals(List.of("모집 중", "마감", "정원 참"), cards.stream().map(MeetCardDto::getTitle).toList());
        assertEquals(2, cards.get(0).getCurrentParticipants());
        assertFalse(cards.get(0).isClosed());
        assertTrue(cards.get(1).isClosed());
        assertTrue(cards.get(2).isClosed());
    }

    // ---------- 알림 ----------

    @Test
    void recentNotificationsAreNewestFirstAndLimitedTo10() {
        for (int i = 1; i <= 12; i++) {
            notifications.save(Notification.companionAccepted(me.getUserId(), (long) i, "모집 " + i));
        }
        notifications.save(Notification.companionAccepted(data.user("남").getUserId(), 1L, "남의 알림"));
        flushAndClear();

        List<NotificationView> recent = notificationService.getRecent(me.getUserId());

        assertEquals(10, recent.size());
        assertTrue(recent.get(0).message().contains("모집 12"));
        assertEquals(12L, notificationService.countUnread(me.getUserId()));
    }

    @Test
    void markAllReadOnlyTouchesMyNotifications() {
        User other = data.user("남");
        notifications.save(Notification.companionAccepted(me.getUserId(), 1L, "A"));
        notifications.save(Notification.companionAccepted(me.getUserId(), 2L, "B"));
        notifications.save(Notification.companionAccepted(other.getUserId(), 3L, "C"));
        flushAndClear();

        notificationService.markAllRead(me.getUserId());

        assertEquals(0L, notificationService.countUnread(me.getUserId()));
        assertEquals(1L, notificationService.countUnread(other.getUserId()));
        assertTrue(notificationService.getRecent(me.getUserId()).stream().allMatch(NotificationView::read));
    }
}
