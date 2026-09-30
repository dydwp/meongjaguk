package com.meongjaguk.app.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 동행 신청 (walk_applications)
 * 김환중(신청·취소)과 최주영(마이페이지 수락·거절) 코드가 함께 쓰는 엔티티
 * - board    : 신청한 게시글(모집) 연결
 * - meetingId: 같은 meeting_id 컬럼을 숫자로 읽기 위한 읽기 전용 필드 (마이페이지 조회용)
 */
@Entity
@Table(name = "walk_applications",
       uniqueConstraints = @UniqueConstraint(name = "uk_walk_applications_meeting_user",
                                             columnNames = {"meeting_id", "user_id"}))
public class CompanionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_applications_meeting"))
    private Board board;

    @Column(name = "meeting_id", insertable = false, updatable = false)
    private Long meetingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_walk_applications_user"))
    private User applicant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private ApplicationStatus status = ApplicationStatus.PENDING;

    @Column(length = 500)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected CompanionRequest() {}

    /** 동행 신청 생성: 신청 직후 상태는 PENDING */
    public static CompanionRequest create(Board board, User applicant) {
        CompanionRequest request = new CompanionRequest();
        request.board = board;
        request.meetingId = board.getMeetingId();
        request.applicant = applicant;
        request.status = ApplicationStatus.PENDING;
        return request;
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

    /** 수락 (최주영: 마이페이지 수락) */
    public void accept() {
        this.status = ApplicationStatus.ACCEPTED;
    }

    /** 거절 (최주영: 마이페이지 거절) */
    public void reject() {
        this.status = ApplicationStatus.REJECTED;
    }

    public Long getId() { return id; }
    public Board getBoard() { return board; }
    public Long getMeetingId() { return meetingId; }
    public User getApplicant() { return applicant; }
    public ApplicationStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
