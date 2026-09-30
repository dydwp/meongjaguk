package com.mungjaguk.app.integration;

import com.mungjaguk.app.dto.MeetingRequestView;
import com.mungjaguk.app.dto.MyCompanionRequestView;
import com.mungjaguk.app.dto.MySharedMeetingView;
import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.MyPageQueryRepository;
import com.mungjaguk.app.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 마이페이지 SQL(JdbcTemplate) 조회: 인원 집계, 정렬 순서, 본인 데이터만 */
class MyPageQueryIntegrationTest extends IntegrationTestSupport {

    @Autowired MyPageQueryRepository myPage;

    private User host;
    private User me;

    @BeforeEach
    void setUp() {
        host = data.user("용제");
        me = data.user("민준");
    }

    @Test
    void sharedMeetingsCountOnlyAcceptedApplicants() {
        Board board = data.board(host, "저녁 산책", LocalDateTime.now().plusDays(1), 5);
        data.acceptedRequest(board, me);
        data.acceptedRequest(board, data.user("서연"));
        data.pendingRequest(board, data.user("지훈"));
        data.rejectedRequest(board, data.user("하준"));
        Board empty = data.board(host, "아침 산책", LocalDateTime.now().plusDays(2), 3);
        data.board(me, "남의 모집", LocalDateTime.now().plusDays(1), 3);
        flushAndClear();

        List<MySharedMeetingView> shared = myPage.findMySharedMeetings(host.getUserId());

        assertEquals(2, shared.size());
        MySharedMeetingView withApplicants = shared.stream()
                .filter(m -> m.meetingId().equals(board.getMeetingId())).findFirst().orElseThrow();
        assertEquals(3, withApplicants.currentParticipants()); // 작성자 + 수락 2
        assertEquals(5, withApplicants.maxParticipants());
        assertEquals("저녁 산책 코스", withApplicants.courseName());
        assertEquals("RECRUITING", withApplicants.status());
        MySharedMeetingView noApplicants = shared.stream()
                .filter(m -> m.meetingId().equals(empty.getMeetingId())).findFirst().orElseThrow();
        assertEquals(1, noApplicants.currentParticipants());
    }

    @Test
    void receivedRequestsPutActionableFirstAndPastLast() {
        Board soon = data.board(host, "곧 산책", LocalDateTime.now().plusDays(1), 5);
        Board later = data.board(host, "나중 산책", LocalDateTime.now().plusDays(3), 5);
        Board past = data.board(host, "지난 산책", LocalDateTime.now().minusDays(1), 5);
        long pastPending = data.pendingRequest(past, data.user("A")).getId();
        long laterAccepted = data.acceptedRequest(later, data.user("B")).getId();
        long laterPending = data.pendingRequest(later, data.user("C")).getId();
        long soonRejected = data.rejectedRequest(soon, data.user("D")).getId();
        long soonPending = data.pendingRequest(soon, data.user("E")).getId();
        data.pendingRequest(data.board(me, "남의 모집", LocalDateTime.now().plusDays(1), 3), data.user("F"));
        flushAndClear();

        List<MeetingRequestView> received = myPage.findRequestsForMyMeetings(host.getUserId());

        // 대기(가까운 모임 먼저) → 수락 → 거절 → 지난 모임
        assertEquals(List.of(soonPending, laterPending, laterAccepted, soonRejected, pastPending),
                received.stream().map(MeetingRequestView::applicationId).toList());
        assertEquals("E", received.get(0).applicantNickname());
        assertEquals("곧 산책", received.get(0).meetingTitle());
        assertTrue(received.get(4).closed());
    }

    @Test
    void sentRequestsPutAcceptedFirstAndShowHost() {
        long past = data.acceptedRequest(data.board(host, "지난 산책", LocalDateTime.now().minusDays(2), 5), me).getId();
        long pending = data.pendingRequest(data.board(host, "대기 산책", LocalDateTime.now().plusDays(1), 5), me).getId();
        long accepted = data.acceptedRequest(data.board(host, "확정 산책", LocalDateTime.now().plusDays(5), 5), me).getId();
        long rejected = data.rejectedRequest(data.board(host, "거절 산책", LocalDateTime.now().plusDays(1), 5), me).getId();
        data.pendingRequest(data.board(host, "남의 신청", LocalDateTime.now().plusDays(1), 5), data.user("서연"));
        flushAndClear();

        List<MyCompanionRequestView> sent = myPage.findMyCompanionRequests(me.getUserId());

        assertEquals(List.of(accepted, pending, rejected, past),
                sent.stream().map(MyCompanionRequestView::applicationId).toList());
        assertEquals("용제", sent.get(0).hostNickname());
        assertEquals("확정 산책 코스", sent.get(0).courseName());
        assertTrue(sent.get(3).past());
    }
}
