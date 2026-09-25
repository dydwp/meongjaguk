package com.mungjaguk.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 동행 신청 (walk_applications)
 */
@Entity
@Table(name = "walk_applications",
       uniqueConstraints = @UniqueConstraint(name = "uk_walk_applications_meeting_user",
                                             columnNames = {"meeting_id", "user_id"}))
public class WalkApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_applications_meeting"))
    private WalkMeeting meeting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_applications_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private ApplicationStatus status;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected WalkApplication() {}

    /** 동행 신청 생성: 신청 직후 상태는 PENDING */
    public static WalkApplication create(WalkMeeting meeting, User user) {
        WalkApplication application = new WalkApplication();
        application.meeting = meeting;
        application.user = user;
        application.status = ApplicationStatus.PENDING;
        return application;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getApplicationId() { return applicationId; }
    public WalkMeeting getMeeting() { return meeting; }
    public User getUser() { return user; }
    public ApplicationStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
