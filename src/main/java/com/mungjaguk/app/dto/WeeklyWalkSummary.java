package com.mungjaguk.app.dto;

/**
 * 메인 배너 "이번 주 나의 산책" 요약 (담당: 박용제)
 * - 이번 주 = 월요일 0시부터 지금까지 완료한 산책
 * - lastWalkLabel: 마지막 산책 "오늘 2.1km", "어제 1.5km", "9/25 3.0km" (기록이 없으면 null)
 */
public record WeeklyWalkSummary(
        int walkCount,
        double distanceKm,
        int minutes,
        String lastWalkLabel) {
}
