package com.meongjaguk.app.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.meongjaguk.app.security.LoginUser;
import com.meongjaguk.app.service.NotificationService;

/**
 * 헤더 종 아이콘 알림 API (로그인 회원만) - 담당: 박용제
 */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 안 읽은 수 + 최신순 10개: { "unreadCount": 2, "items": [...] } */
    @GetMapping("/api/notifications")
    public ResponseEntity<?> notifications(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요해요."));
        }
        Long userId = loginUser.getUserId();
        return ResponseEntity.ok(Map.of(
                "unreadCount", notificationService.countUnread(userId),
                "items", notificationService.getRecent(userId)));
    }

    /** 알림 창을 열면 전부 읽음 처리 */
    @PostMapping("/api/notifications/read")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        notificationService.markAllRead(loginUser.getUserId());
        return ResponseEntity.noContent().build();
    }
}
