package com.mungjaguk.app.service;

import com.mungjaguk.app.entity.ApplicationStatus;
import com.mungjaguk.app.entity.Board;
import com.mungjaguk.app.entity.BoardStatus;
import com.mungjaguk.app.entity.CompanionRequest;
import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.repository.BoardRepository;
import com.mungjaguk.app.repository.CompanionRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * 동행 신청 / 신청 취소
 */
@Service
@Transactional(readOnly = true)
public class CompanionService {

    private final BoardRepository boardRepository;
    private final CompanionRequestRepository companionRequestRepository;
    private final UserService userService;

    public CompanionService(BoardRepository boardRepository,
                            CompanionRequestRepository companionRequestRepository,
                            UserService userService) {
        this.boardRepository = boardRepository;
        this.companionRequestRepository = companionRequestRepository;
        this.userService = userService;
    }

    /**
     * 동행 신청
     * - 본인이 공유한 모집에는 신청 불가
     * - RECRUITING 상태에서만 신청 가능
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
        if (companionRequestRepository.existsByMeetingIdAndApplicant_UserId(meetingId, userId)) {
            throw new IllegalStateException("이미 신청한 모집이에요.");
        }

        User user = userService.findById(userId);
        try {
            companionRequestRepository.saveAndFlush(CompanionRequest.create(board, user));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 요청된 경우 유니크 제약(uk_walk_applications_meeting_user)에 걸림
            throw new IllegalStateException("이미 신청한 모집이에요.");
        }
    }

    /**
     * 동행 신청 취소
     * - PENDING 상태에서만 취소 가능
     * - 취소 시 신청 행 삭제 (유니크 제약 때문에 재신청이 가능하도록)
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
}
