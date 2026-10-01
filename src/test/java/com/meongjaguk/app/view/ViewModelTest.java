package com.meongjaguk.app.view;

import com.meongjaguk.app.dto.MeetCardDto;
import com.meongjaguk.app.dto.MeetingRequestView;
import com.meongjaguk.app.dto.MyCompanionRequestView;
import com.meongjaguk.app.dto.MySharedMeetingView;
import com.meongjaguk.app.dto.PetCardView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 화면에 보여줄 문구·상태를 만드는 DTO 로직 */
class ViewModelTest {

    private static final LocalDateTime FUTURE = LocalDateTime.of(2099, 9, 27, 16, 0); // 일요일
    private static final LocalDateTime PAST = LocalDateTime.of(2020, 1, 1, 9, 0);

    // ---------- 메인 모집 카드 ----------

    @Test
    void meetCardIsClosedWhenNotRecruitingFullOrPast() {
        assertFalse(meet(FUTURE, 2, 4, "RECRUITING").isClosed());
        assertTrue(meet(FUTURE, 2, 4, "CLOSED").isClosed());
        assertTrue(meet(FUTURE, 4, 4, "RECRUITING").isClosed());
        assertTrue(meet(PAST, 1, 4, "RECRUITING").isClosed());
    }

    @Test
    void meetCardDateTextAndProgress() {
        MeetCardDto card = meet(FUTURE, 3, 4, "RECRUITING");

        assertEquals("9/27(일) 오후 4:00", card.getMeetingDateText());
        assertEquals(75, card.getProgressPercent());
        assertEquals(100, meet(FUTURE, 5, 4, "RECRUITING").getProgressPercent()); // 100% 넘지 않음
        assertEquals(0, meet(FUTURE, 1, 0, "RECRUITING").getProgressPercent());   // 0으로 나누지 않음
    }

    private static MeetCardDto meet(LocalDateTime at, int current, int max, String status) {
        return new MeetCardDto(1L, "모집", "코스", null, at.toLocalDate(), at.toLocalTime(), current, max, status);
    }

    // ---------- 받은 동행 신청 ----------

    @Test
    void receivedRequestLabels() {
        MeetingRequestView pending = received(FUTURE, "PENDING", "민준");
        assertTrue(pending.actionable());
        assertEquals("대기 중", pending.statusLabel());
        assertEquals("pending", pending.statusClass());
        assertEquals("민", pending.avatarInitial());

        MeetingRequestView expired = received(PAST, "PENDING", "민준");
        assertTrue(expired.closed());
        assertFalse(expired.actionable());
        assertEquals("마감됨", expired.statusLabel());
        assertEquals("closed", expired.statusClass());

        assertEquals("수락됨", received(PAST, "ACCEPTED", "민준").statusLabel());
        assertEquals("rejected", received(FUTURE, "REJECTED", "민준").statusClass());
        assertEquals("멍", received(FUTURE, "PENDING", " ").avatarInitial());
    }

    @Test
    void meetingTimeItselfCountsAsClosed() {
        LocalDateTime now = LocalDateTime.now().minusSeconds(1);
        assertTrue(received(now, "PENDING", "민준").closed());
    }

    private static MeetingRequestView received(LocalDateTime at, String status, String nickname) {
        return new MeetingRequestView(1L, 10L, "모집", nickname, null, at.toLocalDate(), at.toLocalTime(), status);
    }

    // ---------- 내가 보낸 동행 신청 ----------

    @Test
    void sentRequestLabels() {
        MyCompanionRequestView waiting = sent(FUTURE, "PENDING", "서연");
        assertFalse(waiting.past());
        assertEquals("신청 대기", waiting.statusLabel());
        assertEquals("2099.09.27 16:00", waiting.scheduleLabel());
        assertEquals("서", waiting.hostAvatarInitial());

        assertEquals("마감됨", sent(PAST, "PENDING", "서연").statusLabel());
        assertEquals("closed", sent(PAST, "PENDING", "서연").statusClass());
        assertEquals("accepted", sent(PAST, "ACCEPTED", "서연").statusClass());
        assertEquals("거절됨", sent(FUTURE, "REJECTED", "서연").statusLabel());
        assertEquals("멍", sent(FUTURE, "PENDING", null).hostAvatarInitial());
    }

    private static MyCompanionRequestView sent(LocalDateTime at, String status, String host) {
        return new MyCompanionRequestView(1L, 10L, "모집", "코스", host, at.toLocalDate(), at.toLocalTime(), status);
    }

    // ---------- 내가 공유한 글 / 반려견 카드 ----------

    @Test
    void sharedMeetingLabels() {
        MySharedMeetingView meeting = new MySharedMeetingView(1L, "모집", "코스",
                LocalDate.of(2026, 9, 28), LocalTime.of(19, 0), 2, 5, "RECRUITING");

        assertEquals("2026.09.28 19:00", meeting.scheduleLabel());
        assertEquals("참여 2 / 정원 5", meeting.participantLabel());
        assertEquals("모집 중", meeting.statusLabel());
        assertEquals("모집 마감", withStatus("CLOSED").statusLabel());
        assertEquals("산책 완료", withStatus("COMPLETED").statusLabel());
        assertEquals("IN_PROGRESS", withStatus("IN_PROGRESS").statusLabel());
    }

    private static MySharedMeetingView withStatus(String status) {
        return new MySharedMeetingView(1L, "모집", "코스", LocalDate.of(2026, 9, 28), LocalTime.of(19, 0), 1, 5, status);
    }

    @Test
    void petSummaryLine() {
        assertEquals("말티즈 · 소형견 · 3세", new PetCardView(1L, "보리", "말티즈", "소형견", 3, null, "").summaryLine());
    }
}
