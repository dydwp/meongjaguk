package com.mungjaguk.app.entity;

import jakarta.persistence.*;

/**
 * 산책 코스 경로 좌표 (course_points)
 * - 추천 산책로를 게시판에 등록할 때 경로 좌표를 순서대로 저장
 */
@Entity
@Table(name = "course_points",
       uniqueConstraints = @UniqueConstraint(name = "uk_course_points_course_sequence",
                                             columnNames = {"course_id", "sequence_no"}))
public class CoursePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "course_point_id")
    private Long coursePointId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_course_points_course"))
    private Route course;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(nullable = false, columnDefinition = "DECIMAL(10,7)")
    private Double latitude;

    @Column(nullable = false, columnDefinition = "DECIMAL(10,7)")
    private Double longitude;

    protected CoursePoint() {}

    public static CoursePoint create(Route course, int sequenceNo, double latitude, double longitude) {
        CoursePoint point = new CoursePoint();
        point.course = course;
        point.sequenceNo = sequenceNo;
        point.latitude = latitude;
        point.longitude = longitude;
        return point;
    }

    public Long getCoursePointId() { return coursePointId; }
    public Route getCourse() { return course; }
    public int getSequenceNo() { return sequenceNo; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}
