package com.meongjaguk.app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 개인 산책에서 선택한 추천 경로 좌표. */
@Entity
@Table(name = "walk_record_planned_points",
        uniqueConstraints = @UniqueConstraint(name = "uk_walk_planned_point_sequence",
                columnNames = {"walk_record_id", "sequence_no"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WalkRecordPlannedPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "walk_record_planned_point_id")
    private Long id;

    @Column(name = "walk_record_id", nullable = false)
    private Long walkRecordId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    public static WalkRecordPlannedPoint of(Long walkRecordId, int sequenceNo,
                                             double latitude, double longitude) {
        WalkRecordPlannedPoint point = new WalkRecordPlannedPoint();
        point.walkRecordId = walkRecordId;
        point.sequenceNo = sequenceNo;
        point.latitude = BigDecimal.valueOf(latitude).setScale(7, RoundingMode.HALF_UP);
        point.longitude = BigDecimal.valueOf(longitude).setScale(7, RoundingMode.HALF_UP);
        return point;
    }
}
