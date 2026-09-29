package com.mungjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record MyCompanionRequestView(
        Long applicationId,
        Long meetingId,
        String meetingTitle,
        String courseName,
        String hostNickname,
        LocalDate meetingDate,
        LocalTime meetingTime,
        String status
) {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public String scheduleLabel() {
        return meetingDate.format(DATE_FORMAT) + " " + meetingTime.format(TIME_FORMAT);
    }

    public String hostAvatarInitial() {
        if (hostNickname == null || hostNickname.isBlank()) {
            return "멍";
        }

        return hostNickname.substring(0, 1);
    }

    public String statusLabel() {
        return switch (status) {
            case "PENDING" -> "신청 대기";
            case "ACCEPTED" -> "수락됨";
            case "REJECTED" -> "거절됨";
            default -> status;
        };
    }

    public String statusClass() {
        return switch (status) {
            case "ACCEPTED" -> "accepted";
            case "REJECTED" -> "rejected";
            default -> "pending";
        };
    }
}