package com.meongjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MeetingRequestView(
        Long applicationId,
        Long meetingId,
        String meetingTitle,
        String applicantNickname,
        String message,
        LocalDate meetingDate,
        LocalTime meetingTime,
        String status,
        String meetingStatus // 모집글 상태 (추가: 김환중)
) {
    // 모집글 상태 없이 만드는 기존 생성자 (추가: 김환중)
    public MeetingRequestView(Long applicationId, Long meetingId, String meetingTitle,
                              String applicantNickname, String message,
                              LocalDate meetingDate, LocalTime meetingTime, String status) {
        this(applicationId, meetingId, meetingTitle, applicantNickname, message,
                meetingDate, meetingTime, status, null);
    }

    public String avatarInitial() {
        if (applicantNickname == null || applicantNickname.isBlank()) {
            return "멍";
        }

        return applicantNickname.substring(0, 1);
    }

    public boolean closed() {
        // 동행 산책이 시작됐거나 끝난 모집글도 지난 신청과 똑같이 마감 처리 (추가: 김환중)
        return !LocalDateTime.now().isBefore(LocalDateTime.of(meetingDate, meetingTime))
                || "IN_PROGRESS".equals(meetingStatus) || "COMPLETED".equals(meetingStatus);
    }

    public boolean pending() {
        return "PENDING".equals(status);
    }

    public boolean actionable() {
        return pending() && !closed();
    }

    public String statusLabel() {
        if (pending() && closed()) {
            return "마감됨";
        }

        return switch (status) {
            case "PENDING" -> "대기 중";
            case "ACCEPTED" -> "수락됨";
            case "REJECTED" -> "거절됨";
            default -> status;
        };
    }

    public String statusClass() {
        if (pending() && closed()) {
            return "closed";
        }

        return switch (status) {
            case "ACCEPTED" -> "accepted";
            case "REJECTED" -> "rejected";
            default -> "pending";
        };
    }
}