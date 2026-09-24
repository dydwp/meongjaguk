package com.mungjaguk.app.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
public class Route {

  @Id
  @Column(name = "course_id", nullable = false)
  private Integer courseId;

  @Column(name = "name", nullable = false)
  private String courseName;

  @Column(name = "description")
  private String description;

  @Column(name = "distance_m")
  private Long distanceM;

  @Column(name = "estimated_minutes")
  private Integer estimatedMinutes;

  @Column(name = "feature")
  private String feature;

  @Column(name = "region")
  private String region;

  @Column(name = "start_latitude")
  private Double startLatitude;

  @Column(name = "start_longitude")
  private Double startLongitude;

  @Column(name = "thumbnail_image")
  private String thumbnailImg;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

}
