package com.mungjaguk.app.dto;

/**
 * 산책 종료 시 브라우저가 보내는 값 - 담당: 박용제
 *
 * @param courseId  추천 코스로 걸었으면 코스 번호, 자유 산책이면 null
 * @param startedAt 시작 시각 (브라우저 Date.now() 값, 밀리초)
 * @param endedAt   종료 시각 (밀리초)
 * @param distanceM 걸은 거리 (m)
 */
public record WalkSaveRequest(
        Long courseId,
        Long startedAt,
        Long endedAt,
        Integer distanceM
) {
}