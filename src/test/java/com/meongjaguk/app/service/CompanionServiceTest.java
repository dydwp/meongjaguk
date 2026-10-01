package com.meongjaguk.app.service;

import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.BoardStatus;
import com.meongjaguk.app.entity.CompanionRequest;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.BoardRepository;
import com.meongjaguk.app.repository.CompanionRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Optional;

import static com.meongjaguk.app.support.Fixtures.board;
import static com.meongjaguk.app.support.Fixtures.request;
import static com.meongjaguk.app.support.Fixtures.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CompanionServiceTest {

    private BoardRepository boards;
    private CompanionRequestRepository requests;
    private UserService users;
    private NotificationService notifications;
    private CompanionService service;

    private final User host = user(1L, "용제");
    private final User applicant = user(2L, "민준");
    private Board board;

    @BeforeEach
    void setUp() {
        boards = mock(BoardRepository.class);
        requests = mock(CompanionRequestRepository.class);
        users = mock(UserService.class);
        notifications = mock(NotificationService.class);
        service = new CompanionService(boards, requests, users, notifications);

        board = board(10L, host, LocalDateTime.now().plusDays(1), 3);
        when(boards.findById(10L)).thenReturn(Optional.of(board));
        when(users.findById(2L)).thenReturn(applicant);
    }

    // ---------- 신청 ----------

    @Test
    void applySavesPendingRequestAndNotifiesHost() {
        service.apply(10L, 2L);

        ArgumentCaptor<CompanionRequest> saved = ArgumentCaptor.forClass(CompanionRequest.class);
        verify(requests).saveAndFlush(saved.capture());
        assertEquals(ApplicationStatus.PENDING, saved.getValue().getStatus());
        assertSame(applicant, saved.getValue().getApplicant());
        assertEquals(10L, saved.getValue().getMeetingId());
        verify(notifications).notifyCompanionRequested(board, applicant);
    }

    @Test
    void cannotApplyToMissingBoard() {
        when(boards.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.apply(99L, 2L));
    }

    @Test
    void hostCannotApplyToOwnBoard() {
        assertApplyRejected(1L, "본인이 공유한 모집에는 신청할 수 없어요.");
    }

    @Test
    void cannotApplyWhenClosed() {
        ReflectionTestUtils.setField(board, "status", BoardStatus.CLOSED);

        assertApplyRejected(2L, "모집이 마감되어 신청할 수 없어요.");
    }

    @Test
    void cannotApplyAfterMeetingTime() {
        Board past = board(10L, host, LocalDateTime.now().minusMinutes(1), 3);
        when(boards.findById(10L)).thenReturn(Optional.of(past));

        assertApplyRejected(2L, "모임 시간이 지나 신청할 수 없어요.");
    }

    @Test
    void cannotApplyWhenFull() {
        when(requests.countByMeetingIdAndStatus(10L, ApplicationStatus.ACCEPTED)).thenReturn(2L); // 1 + 2 = 정원 3

        assertApplyRejected(2L, "정원이 다 차서 신청할 수 없어요.");
    }

    @Test
    void cannotApplyTwice() {
        when(requests.existsByMeetingIdAndApplicant_UserId(10L, 2L)).thenReturn(true);

        assertApplyRejected(2L, "이미 신청한 모집이에요.");
    }

    @Test
    void simultaneousDuplicateApplyIsReportedAsDuplicate() {
        when(requests.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_walk_applications_meeting_user"));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.apply(10L, 2L));

        assertEquals("이미 신청한 모집이에요.", e.getMessage());
        verifyNoInteractions(notifications);
    }

    private void assertApplyRejected(Long userId, String message) {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.apply(10L, userId));
        assertEquals(message, e.getMessage());
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(notifications);
    }

    // ---------- 취소 ----------

    @Test
    void pendingRequestCanBeCancelled() {
        CompanionRequest pending = request(5L, board, applicant, ApplicationStatus.PENDING);
        when(requests.findByMeetingIdAndApplicant_UserId(10L, 2L)).thenReturn(Optional.of(pending));

        service.cancel(10L, 2L);

        verify(requests).delete(pending);
    }

    @Test
    void acceptedRequestCannotBeCancelled() {
        CompanionRequest accepted = request(5L, board, applicant, ApplicationStatus.ACCEPTED);
        when(requests.findByMeetingIdAndApplicant_UserId(10L, 2L)).thenReturn(Optional.of(accepted));

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.cancel(10L, 2L));

        assertEquals("대기 중인 신청만 취소할 수 있어요.", e.getMessage());
        verify(requests, never()).delete(any());
    }

    @Test
    void cancelWithoutRequestIsNotFound() {
        when(requests.findByMeetingIdAndApplicant_UserId(10L, 2L)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> service.cancel(10L, 2L));
    }

    // ---------- 수락 / 거절 ----------

    @Test
    void hostAcceptsAndApplicantIsNotified() {
        CompanionRequest pending = givenRequest(ApplicationStatus.PENDING);

        service.acceptForHost(5L, 1L);

        assertEquals(ApplicationStatus.ACCEPTED, pending.getStatus());
        verify(notifications).notifyCompanionAccepted(pending);
    }

    @Test
    void onlyHostCanAccept() {
        CompanionRequest pending = givenRequest(ApplicationStatus.PENDING);

        assertThrows(AccessDeniedException.class, () -> service.acceptForHost(5L, 2L));
        assertEquals(ApplicationStatus.PENDING, pending.getStatus());
    }

    @Test
    void alreadyHandledRequestIsLeftAlone() {
        CompanionRequest rejected = givenRequest(ApplicationStatus.REJECTED);

        service.acceptForHost(5L, 1L);

        assertEquals(ApplicationStatus.REJECTED, rejected.getStatus());
        verifyNoInteractions(notifications);
    }

    @Test
    void cannotAcceptWhenFull() {
        CompanionRequest pending = givenRequest(ApplicationStatus.PENDING);
        when(requests.countByMeetingIdAndStatus(10L, ApplicationStatus.ACCEPTED)).thenReturn(2L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.acceptForHost(5L, 1L));

        assertEquals("모집 정원이 가득 찼습니다.", e.getMessage());
        assertEquals(ApplicationStatus.PENDING, pending.getStatus());
    }

    @Test
    void cannotAcceptOrRejectAfterMeetingTime() {
        board = board(10L, host, LocalDateTime.now().minusHours(1), 3);
        givenRequest(ApplicationStatus.PENDING);

        assertThrows(IllegalStateException.class, () -> service.acceptForHost(5L, 1L));
        assertThrows(IllegalStateException.class, () -> service.rejectForHost(5L, 1L));
    }

    @Test
    void hostRejectsWithoutNotification() {
        CompanionRequest pending = givenRequest(ApplicationStatus.PENDING);

        service.rejectForHost(5L, 1L);

        assertEquals(ApplicationStatus.REJECTED, pending.getStatus());
        verifyNoInteractions(notifications);
    }

    @Test
    void onlyHostCanReject() {
        givenRequest(ApplicationStatus.PENDING);

        assertThrows(AccessDeniedException.class, () -> service.rejectForHost(5L, 3L));
    }

    @Test
    void missingRequestIsBadRequest() {
        when(requests.findById(404L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.acceptForHost(404L, 1L));
    }

    private CompanionRequest givenRequest(ApplicationStatus status) {
        CompanionRequest request = request(5L, board, applicant, status);
        when(requests.findById(5L)).thenReturn(Optional.of(request));
        return request;
    }
}
