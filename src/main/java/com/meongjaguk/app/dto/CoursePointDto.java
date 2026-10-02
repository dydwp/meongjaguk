package com.meongjaguk.app.dto;

/**
 * 경로 좌표 한 점 (등록 요청 / 게시판 응답 공용)
 * - 추천 결과(route.points)와 같은 이름: sequence, latitude, longitude
 */
public record CoursePointDto(
    Integer sequence,
    Double latitude,
    Double longitude) {
}
