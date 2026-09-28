package com.mungjaguk.app.dto;

/** 마이페이지 "활동 내역" 탭 목록 1줄 */
public record WalkHistoryItemView(
        Long id,
        String title,
        String tagLabel,      // 개인 산책 / 공유 참여
        String distanceLabel, // "2.3km"
        String durationLabel, // "약 35분"
        String dateLabel      // "2026.09.19"
) {
}
