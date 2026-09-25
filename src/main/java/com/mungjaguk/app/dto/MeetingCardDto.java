package com.mungjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 산책로 게시판 카드
 * currentParticipants = 작성자 1명 + 수락된 신청자 수
 */
public record MeetingCardDto(
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
    String status) {
}
