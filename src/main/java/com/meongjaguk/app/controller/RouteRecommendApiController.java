package com.meongjaguk.app.controller;

import com.meongjaguk.app.dto.RouteRecommendRequest;
import com.meongjaguk.app.service.AiRouteClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * AI 산책로 추천 중계 API - 담당: 박용제
 * 메인(home-routes.js) / 추천 산책로(walk-recommend.js) 화면이 호출 → AI 서버에 그대로 전달
 * 비회원도 사용 (SecurityConfig 에서 허용)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class RouteRecommendApiController {

    private final AiRouteClient aiRouteClient;

    @PostMapping("/api/routes/recommend")
    public ResponseEntity<String> recommend(@RequestBody RouteRecommendRequest request) {
        request.validate();
        String body = aiRouteClient.recommend(request);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    /** 잘못된 위치 / 개수 → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    /** AI 서버가 꺼져 있거나 오류 응답 → 503 */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<Map<String, String>> handleAiServerError(RestClientException e) {
        log.warn("AI 산책로 추천 호출 실패: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("message", "산책로 추천 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."));
    }
}
