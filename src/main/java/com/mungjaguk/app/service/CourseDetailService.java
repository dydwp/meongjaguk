package com.mungjaguk.app.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.mungjaguk.app.repository.CourseDetailRepository;
import com.mungjaguk.app.dto.RouteDto;
import com.mungjaguk.app.entity.Route;

@Service
public class CourseDetailService {
  private final CourseDetailRepository repository;

  public CourseDetailService(CourseDetailRepository repository) {
    this.repository = repository;
  }

  public void postCourseDetail(RouteDto dto) {
    Route routeInfo = new Route();

    routeInfo.setCourseName(dto.name());
    routeInfo.setDescription(dto.description());
    routeInfo.setDistanceM(dto.distanceM());
    routeInfo.setEstimatedMinutes(dto.estimatedMinutes());
    routeInfo.setFeature(dto.feature());
    routeInfo.setRegion(dto.region());
    routeInfo.setStartLatitude(dto.startLatitude());
    routeInfo.setStartLongitude(dto.startLongitude());
    routeInfo.setThumbnailImg(null);
    routeInfo.setCreatedAt(LocalDateTime.now());

    repository.save(routeInfo);
  }
}
