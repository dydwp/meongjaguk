package com.mungjaguk.app.controller;

import com.mungjaguk.app.dto.WalkSaveRequest;
import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.WalkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 산책 기록 API (화면이 아니라 JSON만 주고받음) - 담당: 박용제
 * WalkController(화면)와 나눔
 */
@RestController
@RequestMapping("/api/walks")
@RequiredArgsConstructor
public class WalkApiController {

    private final WalkService walkService;

    /** 산책 종료 → 기록 저장 (회원만) */
    @PostMapping
    public ResponseEntity<Map<String, Long>> saveWalk(@AuthenticationPrincipal LoginUser loginUser,
                                                      @RequestBody WalkSaveRequest request) {
        if (loginUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long walkRecordId = walkService.saveCompletedWalk(loginUser.getUserId(), request);
        return ResponseEntity.ok(Map.of("walkRecordId", walkRecordId));
    }

    /** 잘못된 값이 오면 400과 이유를 돌려줌 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}