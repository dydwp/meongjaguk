package com.mungjaguk.app.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mungjaguk.app.entity.Route;

public interface RouteRepository extends JpaRepository<Route, Integer> {

  public Route findByCourseId(Long course_id);
}
