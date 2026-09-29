package com.mungjaguk.app.controller;

import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 메인 홈 화면.
 * 배너(산책 시작 + 이번 주 나의 산책) / 추천 산책로 / 산책 지도 / 같이 걷기 모집 /
 * 산책 날씨·나가기 전 체크 등 여러 영역을 한 화면에 모아
 * 보여주는 공용 페이지라 특정 담당자 없이 별도 컨트롤러로 둡니다.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int RECOMMEND_COUNT = 3; // 메인에 보여줄 추천 산책로 수
    private static final int MEET_COUNT = 3;      // 메인에 보여줄 모집 카드 수

    private final MainService mainService;

    @GetMapping("/")
    public String home(Model model, @AuthenticationPrincipal LoginUser loginUser) {
        model.addAttribute("recommendRoutes", mainService.getRecommendRoutes(RECOMMEND_COUNT));
        model.addAttribute("meetCards", mainService.getRecentMeets(MEET_COUNT));
        // 로그인 회원만: 이번 주 산책 요약, 인사말에 넣을 반려견 이름
        if (loginUser != null) {
            model.addAttribute("weeklyWalk", mainService.getWeeklyWalkSummary(loginUser.getUserId()));
            model.addAttribute("petWith", mainService.getPetWith(loginUser.getUserId()));
        }
        return "index";
    }
}