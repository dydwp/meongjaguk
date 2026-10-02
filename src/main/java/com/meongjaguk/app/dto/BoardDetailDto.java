package com.meongjaguk.app.dto;

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
 * startLatitude/startLongitude/points: 지도 표시용 (경로 좌표가 없으면 points는 빈 목록)
 * pets: 모집자의 반려견 정보
 * startedAt/endedAt: 동행 산책 시작·종료 시각 (시작 전이면 null) (추가: 김환중)
 */
public record BoardDetailDto(
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
    String myApplicationStatus,
    Double startLatitude,
    Double startLongitude,
    List<CoursePointDto> points,
    List<PetCardView> pets,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {
}
