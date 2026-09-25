package com.mungjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 공유 산책로 상세
 * participantNicknames: 작성자 + 수락된 신청자 (작성자가 첫 번째)
 * currentParticipants  = 작성자 1명 + 수락된 신청자 수
 * isHost               : 로그인 사용자가 작성자인지
 * myApplicationStatus  : 로그인 사용자의 신청 상태 (신청 안 했거나 비로그인이면 null)
 */
public record MeetingDetailDto(
    Long meetingId,
    String title,
    String description,
    String courseName,
    Long distanceM,
    Integer estimatedMinutes,
    LocalDate meetingDate,
    LocalTime meetingTime,
    boolean petRequired,
    String participationCondition,
    String hostNickname,
    LocalDateTime createdAt,
    List<String> participantNicknames,
    int currentParticipants,
    int maxParticipants,
    String status,
    boolean isHost,
    String myApplicationStatus) {
}
