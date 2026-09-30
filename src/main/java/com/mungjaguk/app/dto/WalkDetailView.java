package com.mungjaguk.app.dto;

public record WalkDetailView(
        Long id,
        String title,
        String tagLabel,
        boolean hasRoute, // 추천 코스가 연결된 산책인지 여부
        boolean deletable, // 완료된 기록에만 삭제버튼을 보여주기 위해 구분
        String description,
        String plannedDistanceLabel, // "거리 · 2.3km"
        String plannedDurationLabel, // "예상 소요시간 · 약 35분"
        String petNamesLabel,
        String actualDistanceLabel, // "2.4 km"
        String actualDurationLabel, // "38:12"
        String completedDateLabel // "2026.09.19 완료"
) {
}
