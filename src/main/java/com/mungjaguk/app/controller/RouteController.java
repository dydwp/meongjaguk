package com.mungjaguk.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.service.RouteService;

@Controller
public class RouteController {

    private final RouteService service;

    public RouteController(RouteService service) {
        this.service = service;
    }

    @GetMapping("/routes")
    public String courseList() {
        return "course/list";
    }

    @GetMapping("/api/courses/routes")
    public ResponseEntity<List<Route>> routes() {
        List<Route> courses = service.getCoursesList();
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/courses-detail")
    public String courseDetail() {
        return "course/detail";
    }
}
