package com.mungjaguk.app.dto;

import java.util.List;

/**
 * 산책 종료 시 브라우저가 보내는 값 - 담당: 박용제
 *
 * @param courseId  DB에 등록된 코스로 걸었으면 코스 번호, 그 외에는 null
 * @param startedAt 시작 시각 (브라우저 Date.now() 값, 밀리초)
 * @param endedAt   종료 시각 (밀리초)
 * @param distanceM 걸은 거리 (m)
 * @param points    지나간 GPS 좌표 목록 (없으면 null 또는 빈 목록)
 * @param recommendedRoute 추천 경로에서 시작한 경우 코스 정보와 좌표
 */
public record WalkSaveRequest(
        Long courseId,
        Long startedAt,
        Long endedAt,
        Integer distanceM,
        List<Point> points,
        RecommendedRoute recommendedRoute
) {

    /** 좌표 한 점 - 브라우저 localStorage에 쌓인 {lat, lng, t} 그대로 */
    public record Point(Double lat, Double lng, Long t) {
    }

    public record RecommendedRoute(
            String title,
            String description,
            Long distanceM,
            Integer estimatedMinutes,
            List<CoursePointDto> points) {
    }
}
