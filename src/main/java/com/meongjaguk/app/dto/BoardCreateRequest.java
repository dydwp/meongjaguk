package com.meongjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 산책로 게시글(동행 모집) 등록 요청
 * - 기존 코스를 공유하면 courseId, 추천받은 새 코스를 공유하면 course 에 코스 정보를 담아 보냄
 * - 새 코스는 게시글과 함께 한 트랜잭션으로 저장됨
 * - points: 새 코스의 경로 좌표 (추가: 김환중, 게시판 지도 경로 표시용, 없으면 출발 지점만 표시)
 * - petIds: 모집자가 함께 산책할 반려견 ID 목록, 선택 사항 (담당: 최주영)
 */
public record BoardCreateRequest(
        Integer courseId,
        RouteDto course,
        String title,
        LocalDate meetingDate,
        LocalTime meetingTime,
        Integer maxParticipants,
        Boolean petRequired,
        String participationCondition,
        String description,
        List<CoursePointDto> points,
        List<Long> petIds) {
}
