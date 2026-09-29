package com.mungjaguk.app.service;

import org.springframework.stereotype.Service;

import com.mungjaguk.app.repository.CourseDetailRepository;

@Service
public class CourseDetailService {
  private final CourseDetailRepository repository;

  public CourseDetailService(CourseDetailRepository repository) {
    this.repository = repository;
  }
}
