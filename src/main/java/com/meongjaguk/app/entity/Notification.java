package com.meongjaguk.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 알림 (notifications 테이블) - 담당: 박용제
 * 종류 3가지 (reference_id는 모두 모집 글 meeting_id)
 *  - COMPANION_ACCEPTED : 동행 신청 수락 → 신청자에게
 *  - COMPANION_REQUESTED: 동행 신청 도착 → 모집 작성자에게
 *  - COMMENT_ADDED      : 내 모집 글에 댓글 → 모집 작성자에게
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    public static final String TYPE_COMPANION_ACCEPTED = "COMPANION_ACCEPTED";
    public static final String TYPE_COMPANION_REQUESTED = "COMPANION_REQUESTED";
    public static final String TYPE_COMMENT_ADDED = "COMMENT_ADDED";

    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int COMMENT_PREVIEW_LENGTH = 40; // 댓글 알림에 보여줄 앞부분 길이

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "user_id", nullable = false)
    private Long userId; // 알림을 받는 사람

    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "reference_id")
    private Long referenceId; // 모집 글(meeting_id)

    @Column(name = "title", nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "message", nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 동행 신청 수락: 신청자에게 "'모집 제목' 모집에 함께 걷게 됐어요" */
    public static Notification companionAccepted(Long applicantUserId, Long meetingId, String meetingTitle) {
        return create(applicantUserId, TYPE_COMPANION_ACCEPTED, meetingId,
                "동행 신청이 수락됐어요",
                "'" + meetingTitle + "' 모집에 함께 걷게 됐어요.");
    }

    /** 동행 신청 도착: 모집 작성자에게 "○○님이 '모집 제목'에 동행을 신청했어요" */
    public static Notification companionRequested(Long hostUserId, Long meetingId,
                                                  String applicantNickname, String meetingTitle) {
        return create(hostUserId, TYPE_COMPANION_REQUESTED, meetingId,
                "새 동행 신청이 왔어요",
                applicantNickname + "님이 '" + meetingTitle + "'에 동행을 신청했어요.");
    }

    /** 댓글: 모집 작성자에게 "○○님: 댓글 앞부분…" */
    public static Notification commentAdded(Long hostUserId, Long meetingId,
                                            String commenterNickname, String content) {
        String preview = content.length() > COMMENT_PREVIEW_LENGTH
                ? content.substring(0, COMMENT_PREVIEW_LENGTH) + "…" : content;
        return create(hostUserId, TYPE_COMMENT_ADDED, meetingId,
                "새 댓글이 달렸어요",
                commenterNickname + "님: " + preview);
    }

    private static Notification create(Long userId, String type, Long referenceId, String title, String message) {
        Notification notification = new Notification();
        notification.userId = userId;
        notification.type = type;
        notification.referenceId = referenceId;
        notification.title = title;
        notification.message = message.length() > MAX_MESSAGE_LENGTH
                ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        return notification;
    }
}
