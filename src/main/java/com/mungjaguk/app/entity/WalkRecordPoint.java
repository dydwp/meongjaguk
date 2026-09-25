package com.mungjaguk.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "walk_record_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalkRecordPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "walk_record_point_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "walk_record_id", nullable = false)
    private WalkRecord walkRecord;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public WalkRecordPoint(WalkRecord walkRecord, int sequenceNo, BigDecimal latitude,
                           BigDecimal longitude, LocalDateTime recordedAt) {
        this.walkRecord = walkRecord;
        this.sequenceNo = sequenceNo;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = recordedAt;
    }
}