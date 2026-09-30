package com.meongjaguk.app.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 산책로 게시판 게시글 = 같이 걷기 모집 (walk_meetings)
 */
@Entity
@Table(name = "walk_meetings")
public class Board {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "meeting_id")
    private Long meetingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_meetings_host_user"))
    private User host;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_meetings_course"))
    private Route course;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "meeting_date", nullable = false)
    private LocalDate meetingDate;

    @Column(name = "meeting_time", nullable = false)
    private LocalTime meetingTime;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    @Column(name = "pet_required", nullable = false)
    private boolean petRequired;

    @Column(name = "participation_condition", length = 500)
    private String participationCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private BoardStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static final int MAX_TITLE_LENGTH = 150;
    public static final int MAX_DESCRIPTION_LENGTH = 600;
    public static final int MAX_CONDITION_LENGTH = 500;
    public static final int MIN_PARTICIPANTS = 2;
    public static final int MAX_PARTICIPANTS = 10;

    protected Board() {}

    public static Board create(User host, Route course, String title, String description,
                               LocalDate meetingDate, LocalTime meetingTime, int maxParticipants,
                               boolean petRequired, String participationCondition) {
        Board board = new Board();
        board.host = host;
        board.course = course;
        board.title = title;
        board.description = description;
        board.meetingDate = meetingDate;
        board.meetingTime = meetingTime;
        board.maxParticipants = maxParticipants;
        board.petRequired = petRequired;
        board.participationCondition = participationCondition;
        return board;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.status == null) {
            this.status = BoardStatus.RECRUITING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isHostedBy(Long userId) {
        return host != null && host.getUserId().equals(userId);
    }

    /**
     * 모집 마감: 모집 중(RECRUITING)일 때만 CLOSED로 변경
     * - 정원 충족 시 자동 마감, 작성자 수동 마감에서 호출
     */
    public void close() {
        if (this.status == BoardStatus.RECRUITING) {
            this.status = BoardStatus.CLOSED;
        }
    }

    /**
     * 게시글 수정 (작성자만, 코스는 바꾸지 않음)
     * - 모임 일시가 지나 마감(CLOSED)됐던 글도 새 일시로 바꾸면 다시 모집 중으로 변경
     */
    public void update(String title, String description, LocalDate meetingDate, LocalTime meetingTime,
                       int maxParticipants, boolean petRequired, String participationCondition) {
        this.title = title;
        this.description = description;
        this.meetingDate = meetingDate;
        this.meetingTime = meetingTime;
        this.maxParticipants = maxParticipants;
        this.petRequired = petRequired;
        this.participationCondition = participationCondition;
        if (this.status == BoardStatus.CLOSED) {
            this.status = BoardStatus.RECRUITING;
        }
    }

    /** 정원이 찼는지 (현재 인원 = 작성자 1명 + 수락된 신청자 수) */
    public boolean isFull(long acceptedCount) {
        return 1 + acceptedCount >= maxParticipants;
    }

    /** 모임 일시가 지났는지 (모임 시작 시각이 되면 지난 것으로 봄) */
    public boolean isMeetingTimePassed(LocalDateTime now) {
        return !now.isBefore(LocalDateTime.of(meetingDate, meetingTime));
    }

    public Long getMeetingId() { return meetingId; }
    public User getHost() { return host; }
    public Route getCourse() { return course; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getMeetingDate() { return meetingDate; }
    public LocalTime getMeetingTime() { return meetingTime; }
    public int getMaxParticipants() { return maxParticipants; }
    public boolean isPetRequired() { return petRequired; }
    public String getParticipationCondition() { return participationCondition; }
    public BoardStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
