package com.mungjaguk.app.controller;

import com.mungjaguk.app.security.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    // 모든 화면에 로그인 사용자 정보를 "loginUser"로 넘겨줌 (비로그인이면 null)
    @ModelAttribute("loginUser")
    public LoginUser loginUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}