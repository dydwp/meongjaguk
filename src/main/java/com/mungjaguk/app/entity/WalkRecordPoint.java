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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 산책 중 지나간 GPS 좌표 한 점 (walk_record_points 테이블) - 담당: 박용제
 * sequence_no 순서대로 이으면 걸은 경로가 됩니다.
 */
@Entity
@Table(name = "walk_record_points")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalkRecordPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "walk_record_point_id")
    private Long walkRecordPointId;

    @Column(name = "walk_record_id", nullable = false)
    private Long walkRecordId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;       // 1, 2, 3 ... 지나간 순서

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    public static WalkRecordPoint of(Long walkRecordId, int sequenceNo,
                                     double latitude, double longitude,
                                     LocalDateTime recordedAt) {
        WalkRecordPoint point = new WalkRecordPoint();
        point.walkRecordId = walkRecordId;
        point.sequenceNo = sequenceNo;
        point.latitude = BigDecimal.valueOf(latitude).setScale(7, RoundingMode.HALF_UP);
        point.longitude = BigDecimal.valueOf(longitude).setScale(7, RoundingMode.HALF_UP);
        point.recordedAt = recordedAt;
        return point;
    }
}