package com.meongjaguk.app.dto;

/**
 * AI 산책로 추천 요청 - 담당: 박용제
 * 브라우저가 보내는 값 그대로 AI 서버(POST /api/routes/recommend)에 전달
 *
 * @param latitude  현재 위치 위도
 * @param longitude 현재 위치 경도
 * @param top_k     받을 추천 개수 (AI 서버 필드명 그대로)
 */
public record RouteRecommendRequest(
        Double latitude,
        Double longitude,
        Integer top_k
) {

    /** 무한 스크롤로 개수가 늘어나도 AI 서버에 과한 요청이 가지 않도록 상한 */
    public static final int MAX_TOP_K = 100;

    /** 값이 비었거나 범위를 벗어나면 IllegalArgumentException */
    public void validate() {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("위치 값이 올바르지 않습니다.");
        }
        if (top_k == null || top_k < 1 || top_k > MAX_TOP_K) {
            throw new IllegalArgumentException("추천 개수는 1~" + MAX_TOP_K + "개까지 요청할 수 있습니다.");
        }
    }
}
