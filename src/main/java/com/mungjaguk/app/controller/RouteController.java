package com.mungjaguk.app.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.mungjaguk.app.entity.Route;
import com.mungjaguk.app.service.CourseDetailService;
import com.mungjaguk.app.service.RouteService;

@Controller
public class RouteController {

    private final RouteService service;
    private final CourseDetailService detailService;

    public RouteController(RouteService service, CourseDetailService detailService) {
        this.service = service;
        this.detailService = detailService;
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

    @GetMapping("/api/courses/{courseId}")
    public ResponseEntity<Route> courseInfo(@PathVariable("courseId") Long course_id) {
        Route course = service.getCourseInfo(course_id);
        if (course == null)
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok(course);
    }

    // 상세 페이지
    @GetMapping("/course-detail")
    public String courseDetail() {
        return "course/detail";
    }

}
