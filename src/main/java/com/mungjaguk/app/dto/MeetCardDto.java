package com.mungjaguk.app.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 메인 하단 - 같이 걷기 모집 카드 (담당: 박용제)
 * 필드 이름은 walk_meetings 테이블 컬럼에 맞춰 두었습니다.
 * 환중님 엔티티가 나오면 엔티티 → 이 DTO로 변환만 하면 됩니다.
 */
@Getter
@AllArgsConstructor
public class MeetCardDto {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M/d(E) a h:mm", Locale.KOREAN);

    private Long meetingId;                 // meeting_id
    private String title;                   // title
    private String courseName;              // courses.name (코스 이름)
    private String participationCondition;  // participation_condition
    private LocalDate meetingDate;          // meeting_date
    private LocalTime meetingTime;          // meeting_time
    private int currentParticipants;        // 신청 수락된 인원
    private int maxParticipants;            // max_participants
    private String status;                  // RECRUITING, CLOSED 등

    /** 모집 상태가 아니거나, 인원이 다 찼거나, 날짜가 지났으면 마감 */
    public boolean isClosed() {
        LocalDateTime meetingAt = LocalDateTime.of(meetingDate, meetingTime);
        return !"RECRUITING".equals(status)
                || currentParticipants >= maxParticipants
                || meetingAt.isBefore(LocalDateTime.now());
    }

    /** 화면용: "9/27(일) 오후 4:00" */
    public String getMeetingDateText() {
        return LocalDateTime.of(meetingDate, meetingTime).format(DATE_FORMAT);
    }

    /** 진행 바 너비(%) */
    public int getProgressPercent() {
        if (maxParticipants == 0) return 0;
        return Math.min(100, currentParticipants * 100 / maxParticipants);
    }
}