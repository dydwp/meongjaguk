package com.meongjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 산책로 게시판 카드
 * currentParticipants = 작성자 1명 + 수락된 신청자 수
 * startLatitude/startLongitude/points: 지도 표시용 (경로 좌표가 없으면 points는 빈 목록)
 */
public record BoardCardDto(
    Long meetingId,
    String title,
    String hostNickname,
    LocalDateTime createdAt,
    LocalDate meetingDate,
    LocalTime meetingTime,
    Long distanceM,
    Integer estimatedMinutes,
    int currentParticipants,
    int maxParticipants,
    String status,
    Double startLatitude,
    Double startLongitude,
    List<CoursePointDto> points) {
}
