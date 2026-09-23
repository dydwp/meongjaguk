package com.mungjaguk.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 홈 화면.
 * 추천 산책로 / 바로 산책하기 / 같이 걷기 모집 등 여러 영역을 한 화면에 모아
 * 보여주는 공용 페이지라 특정 담당자 없이 별도 컨트롤러로 둡니다.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }
}
