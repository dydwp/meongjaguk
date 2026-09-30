package com.mungjaguk.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 개인 산책 기록 (walk_records 테이블) - 담당: 박용제
 * 산책을 종료할 때 한 번에 저장합니다.
 */
@Entity
@Table(name = "walk_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalkRecord {

    public static final String STATUS_COMPLETED = "COMPLETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "walk_record_id")
    private Long walkRecordId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id")
    private Long courseId; // 자유 산책이면 null

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "planned_title", length = 100)
    private String plannedTitle;

    @Column(name = "planned_description", length = 1000)
    private String plannedDescription;

    @Column(name = "planned_distance_m")
    private Long plannedDistanceM;

    @Column(name = "planned_estimated_minutes")
    private Integer plannedEstimatedMinutes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 끝난 산책 기록 만들기 */
    public static WalkRecord completed(Long userId, Long courseId,
            LocalDateTime startedAt, LocalDateTime endedAt,
            int durationSeconds, int distanceM) {
        WalkRecord record = new WalkRecord();
        record.userId = userId;
        record.courseId = courseId;
        record.status = STATUS_COMPLETED;
        record.startedAt = startedAt;
        record.endedAt = endedAt;
        record.durationSeconds = durationSeconds;
        record.distanceM = distanceM;
        return record;
    }

    public void setPlannedRoute(String title, String description,
                                Long distanceM, Integer estimatedMinutes) {
        this.plannedTitle = title;
        this.plannedDescription = description;
        this.plannedDistanceM = distanceM;
        this.plannedEstimatedMinutes = estimatedMinutes;
    }
}
