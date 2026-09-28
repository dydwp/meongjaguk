package com.mungjaguk.app.dto;

public record WalkDetailView(
                Long id,
                String title,
                String tagLabel,
                boolean hasRoute, // 추천 코스가 연결된 산책인지 여부
                String description,
                String plannedDistanceLabel, // "거리 · 2.3km"
                String plannedDurationLabel, // "예상 소요시간 · 약 35분"
                String actualDistanceLabel, // "2.4 km"
                String actualDurationLabel, // "38:12"
                String completedDateLabel // "2026.09.19 완료"
) {
}
