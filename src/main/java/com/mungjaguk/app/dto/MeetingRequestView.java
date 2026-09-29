package com.mungjaguk.app.dto;

public record MeetingRequestView(
        Long applicationId,
        Long meetingId,
        String meetingTitle,
        String applicantNickname,
        String message,
        String status
) {
    public String avatarInitial() {
        if (applicantNickname == null || applicantNickname.isBlank()) {
            return "멍";
        }

        return applicantNickname.substring(0, 1);
    }

    public String statusLabel() {
        return switch (status) {
            case "PENDING" -> "대기 중";
            case "ACCEPTED" -> "수락됨";
            case "REJECTED" -> "거절됨";
            default -> status;
        };
    }

    public boolean pending() {
        return "PENDING".equals(status);
    }

    public String statusClass() {
        return switch (status) {
            case "ACCEPTED" -> "accepted";
            case "REJECTED" -> "rejected";
            default -> "pending";
        };
    }
}