package com.meongjaguk.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.meongjaguk.app.entity.Route;

public interface RouteRepository extends JpaRepository<Route, Integer> {

  public Route findByCourseId(Long course_id);
}
