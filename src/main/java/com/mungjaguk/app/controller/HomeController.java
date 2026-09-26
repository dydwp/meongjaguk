package com.mungjaguk.app.controller;

import com.mungjaguk.app.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 홈 화면.
 * 추천 산책로 / 바로 산책하기 / 같이 걷기 모집 등 여러 영역을 한 화면에 모아
 * 보여주는 공용 페이지라 특정 담당자 없이 별도 컨트롤러로 둡니다.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int RECOMMEND_COUNT = 3; // 메인에 보여줄 추천 산책로 수

    private final MainService mainService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("recommendRoutes", mainService.getRecommendRoutes(RECOMMEND_COUNT));
        return "index";
    }
}