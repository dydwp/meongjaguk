package com.meongjaguk.app.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.meongjaguk.app.dto.NotificationView;
import com.meongjaguk.app.entity.Board;
import com.meongjaguk.app.entity.CompanionRequest;
import com.meongjaguk.app.entity.Notification;
import com.meongjaguk.app.entity.User;
import com.meongjaguk.app.repository.NotificationRepository;

/**
 * 알림: 저장 / 헤더 알림 창 조회 / 읽음 처리 (담당: 박용제)
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 동행 신청 수락 → 신청자에게 알림 (수락과 같은 트랜잭션에서 호출) */
    @Transactional
    public void notifyCompanionAccepted(CompanionRequest request) {
        notificationRepository.save(Notification.companionAccepted(
                request.getApplicant().getUserId(),
                request.getMeetingId(),
                request.getBoard().getTitle()));
    }

    /** 모집 글 삭제 → 그 글을 가리키는 알림도 삭제 (삭제와 같은 트랜잭션에서 호출) */
    @Transactional
    public void deleteByMeeting(Long meetingId) {
        notificationRepository.deleteByReferenceId(meetingId);
    }

    /** 동행 신청 도착 → 모집 작성자에게 알림 (신청과 같은 트랜잭션에서 호출) */
    @Transactional
    public void notifyCompanionRequested(Board board, User applicant) {
        notificationRepository.save(Notification.companionRequested(
                board.getHost().getUserId(),
                board.getMeetingId(),
                applicant.getNickname(),
                board.getTitle()));
    }

    /**
     * 내 모집 글에 댓글 → 모집 작성자에게 알림 (댓글 저장과 같은 트랜잭션에서 호출)
     * - 작성자가 자기 글에 단 댓글은 알림 없음
     */
    @Transactional
    public void notifyCommentAdded(Board board, User commenter, String content) {
        if (board.isHostedBy(commenter.getUserId())) {
            return;
        }
        notificationRepository.save(Notification.commentAdded(
                board.getHost().getUserId(),
                board.getMeetingId(),
                commenter.getNickname(),
                content));
    }

    /** 헤더 알림 창: 최신순 10개 */
    public List<NotificationView> getRecent(Long userId) {
        return notificationRepository.findTop10ByUserIdOrderByCreatedAtDescNotificationIdDesc(userId)
                .stream()
                .map(n -> new NotificationView(
                        n.getNotificationId(),
                        n.getTitle(),
                        n.getMessage(),
                        linkOf(n),
                        n.isRead(),
                        n.getCreatedAt()))
                .toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /** 알림 창을 열면 전부 읽음 처리 */
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    /** 알림을 누르면 이동할 곳: 동행 신청 도착은 마이페이지 받은 신청 탭, 나머지는 모집 글 상세 */
    private static String linkOf(Notification notification) {
        if (Notification.TYPE_COMPANION_REQUESTED.equals(notification.getType())) {
            return "/mypage?tab=requests";
        }
        if (notification.getReferenceId() != null) {
            return "/course-detail-shared?meetingId=" + notification.getReferenceId();
        }
        return null;
    }
}
