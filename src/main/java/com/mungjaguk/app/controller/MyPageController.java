package com.mungjaguk.app.controller;

import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.PetService;
import com.mungjaguk.app.service.UserService;
import com.mungjaguk.app.service.WalkRecordService;
import java.time.format.DateTimeFormatter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MyPageController {

    private static final DateTimeFormatter JOIN_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM");

    private final UserService userService;
    private final PetService petService;
    private final WalkRecordService walkRecordService;

    public MyPageController(UserService userService, PetService petService,
                            WalkRecordService walkRecordService) {
        this.userService = userService;
        this.petService = petService;
        this.walkRecordService = walkRecordService;
    }

    @GetMapping("/mypage")
    public String mypage(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        Long userId = loginUser.getUserId();
        User user = userService.findById(userId);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("avatarInitial", avatarInitial(user.getNickname()));
        model.addAttribute("joinedLabel", joinedLabel(user));
        model.addAttribute("pets", petService.getMyPets(userId));
        model.addAttribute("walkHistory", walkRecordService.getMyWalkHistory(userId));

        return "member/mypage";
    }

    private String avatarInitial(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "멍";
        }

        return nickname.substring(0, 1);
    }

    private String joinedLabel(User user) {
        String joinedDate = user.getCreatedAt().format(JOIN_DATE_FORMAT);

        return joinedDate + " 가입 · " + providerLabel(user.getProvider()) + " 계정";
    }

    private String providerLabel(String provider) {
        if (provider == null) {
            return "";
        }

        return switch (provider.toLowerCase()) {
            case "kakao" -> "카카오";
            case "naver" -> "네이버";
            case "google" -> "구글";
            default -> provider;
        };
    }
}