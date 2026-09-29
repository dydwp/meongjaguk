package com.mungjaguk.app.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public record MySharedMeetingView(
        Long meetingId,
        String title,
        String courseName,
        LocalDate meetingDate,
        LocalTime meetingTime,
        int currentParticipants,
        int maxParticipants,
        String status
) {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public String scheduleLabel() {
        return meetingDate.format(DATE_FORMAT) + " " + meetingTime.format(TIME_FORMAT);
    }

    public String participantLabel() {
        return "참여 " + currentParticipants + " / 정원 " + maxParticipants;
    }

    public String statusLabel() {
        return switch (status) {
            case "RECRUITING" -> "모집 중";
            case "CLOSED" -> "모집 마감";
            case "COMPLETED" -> "산책 완료";
            default -> status;
        };
    }
}