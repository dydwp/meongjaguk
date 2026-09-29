package com.mungjaguk.app.controller;

import com.mungjaguk.app.entity.User;
import com.mungjaguk.app.security.LoginUser;
import com.mungjaguk.app.service.CompanionService;
import com.mungjaguk.app.service.MyPageService;
import com.mungjaguk.app.service.PetService;
import com.mungjaguk.app.service.UserService;
import com.mungjaguk.app.service.WalkRecordService;
import java.time.format.DateTimeFormatter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MyPageController {

    private static final DateTimeFormatter JOIN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM");

    private final UserService userService;
    private final PetService petService;
    private final WalkRecordService walkRecordService;
    private final MyPageService myPageService;
    private final CompanionService companionService;

    public MyPageController(UserService userService, PetService petService, WalkRecordService walkRecordService,
                            MyPageService myPageService, CompanionService companionService) {
        this.userService = userService;
        this.petService = petService;
        this.walkRecordService = walkRecordService;
        this.myPageService = myPageService;
        this.companionService = companionService;
    }

    @GetMapping("/mypage")
    public String mypage(@AuthenticationPrincipal LoginUser loginUser,
                         @RequestParam(defaultValue = "pets") String tab, Model model) {
        Long userId = loginUser.getUserId();
        User user = userService.findById(userId);

        model.addAttribute("nickname", user.getNickname());
        model.addAttribute("avatarInitial", avatarInitial(user.getNickname()));
        model.addAttribute("joinedLabel", joinedLabel(user));
        model.addAttribute("pets", petService.getMyPets(userId));
        model.addAttribute("sharedMeetings", myPageService.getMySharedMeetings(userId));
        model.addAttribute("meetingRequests", myPageService.getMeetingRequests(userId));
        model.addAttribute("myCompanionRequests", myPageService.getMyCompanionRequests(userId));
        model.addAttribute("walkHistory", walkRecordService.getMyWalkHistory(userId));
        model.addAttribute("activeTab", validTab(tab));

        return "member/mypage";
    }

    @PostMapping("/mypage/requests/{applicationId}/accept")
    public String acceptRequest(@PathVariable Long applicationId,
                                @AuthenticationPrincipal LoginUser loginUser,
                                RedirectAttributes redirectAttributes) {
        try {
            companionService.acceptForHost(applicationId, loginUser.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "참여 신청을 수락했습니다.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/mypage?tab=requests";
    }

    @PostMapping("/mypage/requests/{applicationId}/reject")
    public String rejectRequest(@PathVariable Long applicationId,
                                @AuthenticationPrincipal LoginUser loginUser,
                                RedirectAttributes redirectAttributes) {
        companionService.rejectForHost(applicationId, loginUser.getUserId());
        redirectAttributes.addFlashAttribute("rejectMessage", "참여 신청을 거절했습니다.");

        return "redirect:/mypage?tab=requests";
    }

    private String validTab(String tab) {
        return switch (tab) {
            case "shared", "requests", "activity" -> tab;
            default -> "pets";
        };
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