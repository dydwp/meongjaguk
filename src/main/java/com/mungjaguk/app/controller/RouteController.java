package com.mungjaguk.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 담당 영역: 추천 산책로
 * 추천 코스 목록/상세 조회 관련 Service/Repository는 이 컨트롤러 기준으로 붙여주세요.
 */
@Controller
public class RouteController {

    @GetMapping("/routes")
    public String routes() {
        return "course/list";
    }

    @GetMapping("/course-detail")
    public String courseDetail() {
        return "course/detail";
    }
}
