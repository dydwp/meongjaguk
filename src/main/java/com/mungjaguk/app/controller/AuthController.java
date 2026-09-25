package com.mungjaguk.app.controller;

import com.mungjaguk.app.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 담당 영역: 인증 / 마이페이지 / 반려견 프로필
 *
 * 소셜 로그인 자체는 Spring Security가 처리합니다.
 *  - 로그인 시작: /oauth2/authorization/{kakao|naver|google}
 *  - 로그인 콜백: /login/oauth2/code/{kakao|naver|google}
 *  - 로그아웃  : POST /logout
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser != null) {
            return "redirect:/";
        }
        return "member/login";
    }

    @GetMapping("/mypage")
    public String mypage() {
        return "member/mypage";
    }

    @GetMapping("/pet-profile")
    public String petProfile() {
        return "dog/profile";
    }
}