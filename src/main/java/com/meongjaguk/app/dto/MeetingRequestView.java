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
        String status
) {
    public String avatarInitial() {
        if (applicantNickname == null || applicantNickname.isBlank()) {
            return "멍";
        }

        return applicantNickname.substring(0, 1);
    }

    public boolean closed() {
        return !LocalDateTime.now().isBefore(LocalDateTime.of(meetingDate, meetingTime));
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