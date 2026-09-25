package com.mungjaguk.app.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 같이 걷기 모집 (walk_meetings)
 */
@Entity
@Table(name = "walk_meetings")
public class WalkMeeting {

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
    private MeetingStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WalkMeeting() {}

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.status == null) {
            this.status = MeetingStatus.RECRUITING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isHostedBy(Long userId) {
        return host != null && host.getUserId().equals(userId);
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
    public MeetingStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
