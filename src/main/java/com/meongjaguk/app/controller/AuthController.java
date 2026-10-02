package com.meongjaguk.app.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.meongjaguk.app.security.LoginUser;
import com.meongjaguk.app.service.PetService;

/**
 * 담당 영역: 인증 / 반려견 프로필
 *
 * 소셜 로그인 자체는 Spring Security가 처리합니다.
 *  - 로그인 시작: /oauth2/authorization/{kakao|naver|google}
 *  - 로그인 콜백: /login/oauth2/code/{kakao|naver|google}
 *  - 로그아웃  : POST /logout
 */
@Controller
public class AuthController {

    private final PetService petService;

    public AuthController(PetService petService) {
        this.petService = petService;
    }

    @GetMapping("/login")
    public String login(@AuthenticationPrincipal LoginUser loginUser) {
        if (loginUser != null) {
            return "redirect:/";
        }
        return "member/login";
    }

    @GetMapping("/pet-profile")
    public String petProfile(@RequestParam(required = false) Long id,
            @AuthenticationPrincipal LoginUser loginUser) {

        if (id != null) {
            petService.getPetEditView(id, loginUser.getUserId());
        }

        return "dog/profile";
    }
}