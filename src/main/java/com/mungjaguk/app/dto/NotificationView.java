package com.mungjaguk.app.dto;

import java.time.LocalDateTime;

/**
 * 헤더 알림 창 한 줄 (담당: 박용제)
 * @param link 누르면 이동할 주소 (동행 수락: 모집 글 상세), 없으면 null
 */
public record NotificationView(
        Long notificationId,
        String title,
        String message,
        String link,
        boolean read,
        LocalDateTime createdAt) {
}
