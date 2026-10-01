package com.meongjaguk.app.controller;

import com.meongjaguk.app.security.LoginUser;
import com.meongjaguk.app.service.CompanionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * 동행 신청 API: 신청 / 신청 취소
 * (수락·거절은 마이페이지 담당)
 */
@RestController
public class CompanionController {

    private final CompanionService companionService;

    public CompanionController(CompanionService companionService) {
        this.companionService = companionService;
    }

    /** 동행 신청 */
    @PostMapping("/api/meetings/{meetingId}/applications")
    public ResponseEntity<Map<String, String>> apply(@PathVariable Long meetingId,
                                                     @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인이 필요해요."));
        }
        companionService.apply(meetingId, loginUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "PENDING"));
    }

    /** 동행 신청 취소 */
    @DeleteMapping("/api/meetings/{meetingId}/applications")
    public ResponseEntity<Void> cancel(@PathVariable Long meetingId,
                                       @AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        companionService.cancel(meetingId, loginUser.getUserId());
        return ResponseEntity.noContent().build();
    }

    // ---------- 예외 처리 (이 컨트롤러에만 적용) ----------

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleBadState(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
    }
}
