package com.mungjaguk.app.dto;

import java.util.List;

/**
 * 산책 종료 시 브라우저가 보내는 값 - 담당: 박용제
 *
 * @param courseId  추천 코스로 걸었으면 코스 번호, 자유 산책이면 null
 * @param startedAt 시작 시각 (브라우저 Date.now() 값, 밀리초)
 * @param endedAt   종료 시각 (밀리초)
 * @param distanceM 걸은 거리 (m)
 * @param points    지나간 GPS 좌표 목록 (없으면 null 또는 빈 목록)
 */
public record WalkSaveRequest(
        Long courseId,
        Long startedAt,
        Long endedAt,
        Integer distanceM,
        List<Point> points
) {

    /** 좌표 한 점 - 브라우저 localStorage에 쌓인 {lat, lng, t} 그대로 */
    public record Point(Double lat, Double lng, Long t) {
    }
}