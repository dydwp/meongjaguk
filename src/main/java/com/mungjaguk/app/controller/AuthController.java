package com.mungjaguk.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 담당 영역: 인증 / 마이페이지 / 반려견 프로필
 * 로그인, 회원 정보, 반려견 등록 관련 Service/Repository는 이 컨트롤러 기준으로 붙여주세요.
 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
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
