package com.mungjaguk.app.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.repository.RouteRepository;

import jakarta.transaction.Transactional;

@Service
public class RouteService {
  private final RouteRepository repository;

  public RouteService(RouteRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public List<Route> getCoursesList() {
    return repository.findAll();
  }

  @Transactional
  public Route getCourseInfo(Long course_id) {
    return repository.findByCourseId(course_id);
  }
}
