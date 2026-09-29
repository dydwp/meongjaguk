package com.mungjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 산책로 게시글(동행 모집) 등록 요청
 * - 기존 코스를 공유하면 courseId, 추천받은 새 코스를 공유하면 course 에 코스 정보를 담아 보냄
 * - 새 코스는 게시글과 함께 한 트랜잭션으로 저장됨
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
        String description) {
}
