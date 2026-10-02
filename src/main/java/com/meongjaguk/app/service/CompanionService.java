package com.meongjaguk.app.service;

import com.meongjaguk.app.entity.ApplicationStatus;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.BoardStatus;
import com.meongjaguk.app.entity.CompanionRequest;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.BoardRepository;
import com.meongjaguk.app.repository.CompanionRequestRepository;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동행 신청 / 취소 / 수락 / 거절
 */
@Service
@Transactional(readOnly = true)
public class CompanionService {

    private final BoardRepository boardRepository;
    private final CompanionRequestRepository companionRequestRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public CompanionService(BoardRepository boardRepository, CompanionRequestRepository companionRequestRepository,
                            UserService userService, NotificationService notificationService) {
        this.boardRepository = boardRepository;
        this.companionRequestRepository = companionRequestRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /**
     * 동행 신청
     * - 본인이 공유한 모집에는 신청 불가
     * - RECRUITING 상태에서만 신청 가능
     * - 모임 일시가 지났으면 신청 불가
     * - 정원이 찼으면 신청 불가 (작성자 1명 + 수락된 신청자 수 기준)
     * - 같은 모집에 중복 신청 불가
     * - 신청 후 상태는 PENDING
     */
    @Transactional
    public void apply(Long meetingId, Long userId) {
        Board board = boardRepository.findById(meetingId)
                .orElseThrow(() -> new NoSuchElementException("모집 정보를 찾을 수 없어요."));

        if (board.isHostedBy(userId)) {
            throw new IllegalStateException("본인이 공유한 모집에는 신청할 수 없어요.");
        }
        if (board.getStatus() != BoardStatus.RECRUITING) {
            throw new IllegalStateException("모집이 마감되어 신청할 수 없어요.");
        }
        if (board.isMeetingTimePassed(LocalDateTime.now())) {
            throw new IllegalStateException("모임 시간이 지나 신청할 수 없어요.");
        }
        long accepted = companionRequestRepository.countByMeetingIdAndStatus(meetingId, ApplicationStatus.ACCEPTED);
        if (board.isFull(accepted)) {
            throw new IllegalStateException("정원이 다 차서 신청할 수 없어요.");
        }
        if (companionRequestRepository.existsByMeetingIdAndApplicant_UserId(meetingId, userId)) {
            throw new IllegalStateException("이미 신청한 모집이에요.");
        }

        User user = userService.findById(userId);

        try {
            companionRequestRepository.saveAndFlush(CompanionRequest.create(board, user));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("이미 신청한 모집이에요.");
        }
        notificationService.notifyCompanionRequested(board, user); // 추가(박용제): 모집 작성자에게 알림
    }

    /**
     * 동행 신청 취소
     * - PENDING 상태에서만 취소 가능
     * - 취소 시 신청 행 삭제
     */
    @Transactional
    public void cancel(Long meetingId, Long userId) {
        CompanionRequest request = companionRequestRepository
                .findByMeetingIdAndApplicant_UserId(meetingId, userId)
                .orElseThrow(() -> new NoSuchElementException("신청 내역이 없어요."));

        if (request.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("대기 중인 신청만 취소할 수 있어요.");
        }

        companionRequestRepository.delete(request);
    }

    /**
     * 모집 작성자가 동행 신청 수락
     * - 수락되면 신청자에게 알림 (추가: 박용제)
     */
    @Transactional
    public void acceptForHost(Long applicationId, Long hostUserId) {
        CompanionRequest request = findAuthorizedRequest(applicationId, hostUserId);

        if (request.getStatus() != ApplicationStatus.PENDING) {
            return;
        }

        // 동행 산책을 시작한 뒤에는 수락 불가 (추가: 김환중)
        if (request.getBoard().isWalkStarted()) {
            throw new IllegalStateException("동행 산책이 시작되어 신청을 처리할 수 없습니다.");
        }

        if (request.getBoard().isMeetingTimePassed(LocalDateTime.now())) {
            throw new IllegalStateException("모임 시간이 지나 신청을 처리할 수 없습니다.");
        }

        long acceptedCount = companionRequestRepository
                .countByMeetingIdAndStatus(request.getMeetingId(), ApplicationStatus.ACCEPTED);

        if (1 + acceptedCount >= request.getBoard().getMaxParticipants()) {
            throw new IllegalStateException("모집 정원이 가득 찼습니다.");
        }

        request.accept();
        notificationService.notifyCompanionAccepted(request);
    }

    /**
     * 모집 작성자가 동행 신청 거절
     */
    @Transactional
    public void rejectForHost(Long applicationId, Long hostUserId) {
        CompanionRequest request = findAuthorizedRequest(applicationId, hostUserId);

        if (request.getStatus() != ApplicationStatus.PENDING) {
            return;
        }

        // 동행 산책을 시작한 뒤에는 거절 불가 (추가: 김환중)
        if (request.getBoard().isWalkStarted()) {
            throw new IllegalStateException("동행 산책이 시작되어 신청을 처리할 수 없습니다.");
        }

        if (request.getBoard().isMeetingTimePassed(LocalDateTime.now())) {
            throw new IllegalStateException("모임 시간이 지나 신청을 처리할 수 없습니다.");
        }

        request.reject();
    }

    private CompanionRequest findRequest(Long applicationId) {
        return companionRequestRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("동행 신청을 찾을 수 없습니다."));
    }

    private CompanionRequest findAuthorizedRequest(Long applicationId, Long hostUserId) {
        CompanionRequest request = findRequest(applicationId);

        if (!request.getBoard().isHostedBy(hostUserId)) {
            throw new AccessDeniedException("해당 동행 신청을 처리할 권한이 없습니다.");
        }

        return request;
    }
}